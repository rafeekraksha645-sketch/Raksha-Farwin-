package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiServiceException
import com.example.ai.AiStreamEvent
import com.example.ai.NexoraAiService
import com.example.data.FirestoreChatRepository
import com.example.data.PreferencesRepository
import com.example.model.ChatMessageEntity
import com.example.model.ConversationEntity
import com.example.model.NexoraLanguage
import com.example.model.NexoraMode
import com.example.model.NexoraModelTier
import com.example.model.NexoraPreferencesData
import com.example.model.NexoraResponseStyle
import com.example.model.NexoraThemeMode
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "NexoraViewModel"
private const val DUPLICATE_SUBMIT_DEBOUNCE_MS = 1_500L

sealed interface CloudSyncState {
    data object GuestMode : CloudSyncState
    data object Loading : CloudSyncState
    data object Connected : CloudSyncState
    data class Error(val message: String) : CloudSyncState
}

data class ExhibitionUiState(
    val prompt: String = "",
    val response: String = "",
    val mode: NexoraMode = NexoraMode.GENERAL_IT,
    val isStreaming: Boolean = false,
    val statusText: String? = null,
    val errorMessage: String? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NexoraViewModel(
    private val currentUserId: String?,
    private val userDisplayName: String?,
    private val userEmail: String?,
    private val preferencesRepository: PreferencesRepository,
    private val firestoreRepository: FirestoreChatRepository,
    private val aiService: NexoraAiService
) : ViewModel() {

    val isAuthenticated: Boolean get() = !currentUserId.isNullOrBlank()
    val displayUserName: String get() = userDisplayName?.takeIf { it.isNotBlank() } ?: "Guest Explorer"
    val displayUserEmail: String? get() = userEmail

    val preferences: StateFlow<NexoraPreferencesData> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = NexoraPreferencesData()
        )

    private val _activeMode = MutableStateFlow(NexoraMode.GENERAL_IT)
    val activeMode: StateFlow<NexoraMode> = _activeMode.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _streamingStatus = MutableStateFlow<String?>(null)
    val streamingStatus: StateFlow<String?> = _streamingStatus.asStateFlow()

    private val _streamingModelNote = MutableStateFlow<String?>(null)
    val streamingModelNote: StateFlow<String?> = _streamingModelNote.asStateFlow()

    private val _streamingMessageId = MutableStateFlow<String?>(null)
    private val _streamingBuffer = MutableStateFlow("")

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isFullScreenChat = MutableStateFlow(false)
    val isFullScreenChat: StateFlow<Boolean> = _isFullScreenChat.asStateFlow()

    // In-memory guest session storage (used ONLY when currentUserId == null)
    private val _guestConversations = MutableStateFlow<List<ConversationEntity>>(emptyList())
    private val _guestMessagesMap = MutableStateFlow<Map<String, List<ChatMessageEntity>>>(emptyMap())

    // Retain attached bitmaps per user message ID in memory so Retry / Regenerate work cleanly
    private val sessionBitmaps = LinkedHashMap<String, Bitmap>()
    private var lastSubmittedPrompt: String? = null
    private var lastSubmittedAtMs: Long = 0L
    private var lastExhibitionSubmittedPrompt: String? = null
    private var lastExhibitionSubmittedAtMs: Long = 0L

    private val _cloudSyncState = MutableStateFlow<CloudSyncState>(
        if (isAuthenticated) CloudSyncState.Loading else CloudSyncState.GuestMode
    )
    val cloudSyncState: StateFlow<CloudSyncState> = _cloudSyncState.asStateFlow()

    private var activeGenerationJob: Job? = null
    private var activeExhibitionJob: Job? = null

    private val _exhibitionState = MutableStateFlow(ExhibitionUiState())
    val exhibitionState: StateFlow<ExhibitionUiState> = _exhibitionState.asStateFlow()

    val conversations: StateFlow<List<ConversationEntity>> =
        if (!currentUserId.isNullOrBlank()) {
            firestoreRepository.observeConversations(currentUserId)
                .map { list ->
                    _cloudSyncState.value = CloudSyncState.Connected
                    list
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing conversations", rootError)
                    _cloudSyncState.value = CloudSyncState.Error(
                        rootError.localizedMessage ?: "Failed to sync cloud chat history"
                    )
                    emit(emptyList())
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000L),
                    initialValue = emptyList()
                )
        } else {
            _guestConversations.asStateFlow()
        }

    private val rawMessagesFlow =
        if (!currentUserId.isNullOrBlank()) {
            _activeConversationId.flatMapLatest { convId ->
                if (convId.isNullOrBlank()) {
                    flowOf(emptyList())
                } else {
                    firestoreRepository.observeMessages(currentUserId, convId)
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            val rootError = firestoreError ?: error
                            Log.w(TAG, "Error observing messages", rootError)
                            _chatError.value = rootError.localizedMessage ?: "Failed to load conversation messages."
                            emit(emptyList())
                        }
                }
            }
        } else {
            combine(_activeConversationId, _guestMessagesMap) { convId, map ->
                if (convId.isNullOrBlank()) emptyList() else map[convId].orEmpty()
            }
        }

    val activeMessages: StateFlow<List<ChatMessageEntity>> = combine(
        rawMessagesFlow,
        _streamingMessageId,
        _streamingBuffer,
        _isStreaming
    ) { persisted, streamId, streamText, streaming ->
        if (!streaming || streamId == null) {
            persisted
        } else {
            val idx = persisted.indexOfFirst { it.id == streamId }
            if (idx >= 0) {
                persisted.toMutableList().apply {
                    this[idx] = this[idx].copy(content = streamText)
                }
            } else {
                val convId = _activeConversationId.value.orEmpty()
                val nextSeq = (persisted.maxOfOrNull { it.sequenceIndex } ?: -1L) + 1L
                persisted + ChatMessageEntity(
                    id = streamId,
                    userId = currentUserId.orEmpty(),
                    conversationId = convId,
                    role = "model",
                    content = streamText,
                    mode = _activeMode.value.name,
                    hasError = false,
                    sequenceIndex = nextSeq
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { local ->
                if (_activeConversationId.value == null) {
                    _activeMode.value = local.defaultMode
                }
            }
        }
        if (!currentUserId.isNullOrBlank()) {
            viewModelScope.launch {
                val cloudResult = firestoreRepository.loadCloudPreferences(currentUserId)
                cloudResult.getOrNull()?.let { cloudPrefs ->
                    preferencesRepository.updatePreferences(cloudPrefs)
                }
            }
        }
    }

    fun setFullScreenChat(enabled: Boolean) {
        _isFullScreenChat.value = enabled
    }

    fun dismissError() {
        _chatError.value = null
    }

    fun dismissStatusBanner() {
        _statusBanner.value = null
    }

    fun selectMode(mode: NexoraMode) {
        _activeMode.value = mode
    }

    fun updateLanguage(language: NexoraLanguage) {
        updatePreferences { it.copy(language = language) }
    }

    fun updateThemeMode(themeMode: NexoraThemeMode) {
        updatePreferences { it.copy(themeMode = themeMode) }
    }

    fun updateResponseStyle(style: NexoraResponseStyle) {
        updatePreferences { it.copy(responseStyle = style) }
    }

    fun updateSimpleExplanations(enabled: Boolean) {
        updatePreferences { it.copy(simpleExplanations = enabled) }
    }

    fun updateDefaultMode(mode: NexoraMode) {
        _activeMode.value = mode
        updatePreferences { it.copy(defaultMode = mode) }
    }

    fun updateModelTier(tier: NexoraModelTier) {
        updatePreferences { it.copy(modelTier = tier) }
    }

    private fun updatePreferences(transform: (NexoraPreferencesData) -> NexoraPreferencesData) {
        viewModelScope.launch {
            val updated = transform(preferences.value)
            preferencesRepository.updatePreferences(updated)
            if (!currentUserId.isNullOrBlank()) {
                firestoreRepository.saveCloudPreferences(currentUserId, updated)
            }
        }
    }

    fun startNewChat(mode: NexoraMode = preferences.value.defaultMode) {
        stopGeneration()
        _activeConversationId.value = null
        _activeMode.value = mode
        _chatError.value = null
    }

    fun selectConversation(conversation: ConversationEntity) {
        stopGeneration()
        _activeConversationId.value = conversation.id
        _activeMode.value = conversation.nexoraMode
        _chatError.value = null
    }

    fun sendMessage(
        promptText: String,
        attachedBitmap: Bitmap? = null,
        modeOverride: NexoraMode? = null
    ) {
        val cleanPrompt = promptText.trim()
        if (cleanPrompt.isEmpty() && attachedBitmap == null) return
        val effectivePrompt = cleanPrompt.ifEmpty { "Please analyze the attached technical image." }

        val now = System.currentTimeMillis()
        if (effectivePrompt == lastSubmittedPrompt &&
            attachedBitmap == null &&
            (_isStreaming.value || now - lastSubmittedAtMs < DUPLICATE_SUBMIT_DEBOUNCE_MS)
        ) {
            return
        }
        lastSubmittedPrompt = effectivePrompt
        lastSubmittedAtMs = now

        stopGeneration()
        _chatError.value = null
        modeOverride?.let { _activeMode.value = it }

        val currentMode = _activeMode.value
        val currentPrefs = preferences.value
        val existingMessages = activeMessages.value.filter { !it.hasError }
        val existingConvId = _activeConversationId.value

        activeGenerationJob = viewModelScope.launch {
            val convId = existingConvId ?: createConversationRecord(
                title = deriveConversationTitle(effectivePrompt),
                mode = currentMode,
                language = currentPrefs.language,
                preview = effectivePrompt
            )
            _activeConversationId.value = convId

            val nextSeq = (existingMessages.maxOfOrNull { it.sequenceIndex } ?: -1L) + 1L
            val userMsgId = generateSafeDocId("msg_u")
            val imageHint = if (attachedBitmap != null) "Attached technical screenshot" else null
            if (attachedBitmap != null) {
                rememberSessionBitmap(userMsgId, attachedBitmap)
            }

            persistNewMessage(
                conversationId = convId,
                messageId = userMsgId,
                role = "user",
                content = effectivePrompt,
                mode = currentMode,
                hasError = false,
                sequenceIndex = nextSeq,
                attachedImageHint = imageHint
            )

            val aiMsgId = generateSafeDocId("msg_m")
            streamIntoModelMessage(
                conversationId = convId,
                aiMessageId = aiMsgId,
                aiSequenceIndex = nextSeq + 1L,
                historyForModel = existingMessages,
                userPrompt = effectivePrompt,
                attachedBitmap = attachedBitmap,
                mode = currentMode,
                prefs = currentPrefs,
                totalMessageCount = nextSeq + 2L
            )
        }
    }

    fun regenerateLastResponse() {
        if (_isStreaming.value) return
        val messages = activeMessages.value
        val convId = _activeConversationId.value ?: return
        val lastUser = messages.lastOrNull { it.isUser } ?: return
        val trailingModel = messages.lastOrNull { !it.isUser && it.sequenceIndex > lastUser.sequenceIndex }

        stopGeneration()
        _chatError.value = null

        val historyBeforeTurn = messages.filter { it.sequenceIndex < lastUser.sequenceIndex && !it.hasError }
        val currentMode = _activeMode.value
        val currentPrefs = preferences.value
        val savedBitmap = sessionBitmaps[lastUser.id]

        activeGenerationJob = viewModelScope.launch {
            if (trailingModel != null) {
                deleteSingleMessage(convId, trailingModel.id)
            }
            val aiMsgId = generateSafeDocId("msg_m")
            val seq = lastUser.sequenceIndex + 1L
            streamIntoModelMessage(
                conversationId = convId,
                aiMessageId = aiMsgId,
                aiSequenceIndex = seq,
                historyForModel = historyBeforeTurn,
                userPrompt = lastUser.content,
                attachedBitmap = savedBitmap,
                mode = currentMode,
                prefs = currentPrefs,
                totalMessageCount = (historyBeforeTurn.size + 2).toLong()
            )
        }
    }

    fun retryFailedMessage(failedMessage: ChatMessageEntity) {
        if (_isStreaming.value) return
        val messages = activeMessages.value
        val convId = _activeConversationId.value ?: failedMessage.conversationId.takeIf { it.isNotBlank() } ?: return
        val precedingUser = messages
            .filter { it.isUser && it.sequenceIndex < failedMessage.sequenceIndex }
            .maxByOrNull { it.sequenceIndex } ?: return

        stopGeneration()
        _chatError.value = null

        val historyBeforeTurn = messages.filter { it.sequenceIndex < precedingUser.sequenceIndex && !it.hasError }
        val currentMode = _activeMode.value
        val currentPrefs = preferences.value
        val savedBitmap = sessionBitmaps[precedingUser.id]

        activeGenerationJob = viewModelScope.launch {
            deleteSingleMessage(convId, failedMessage.id)
            val aiMsgId = generateSafeDocId("msg_m")
            streamIntoModelMessage(
                conversationId = convId,
                aiMessageId = aiMsgId,
                aiSequenceIndex = precedingUser.sequenceIndex + 1L,
                historyForModel = historyBeforeTurn,
                userPrompt = precedingUser.content,
                attachedBitmap = savedBitmap,
                mode = currentMode,
                prefs = currentPrefs,
                totalMessageCount = (historyBeforeTurn.size + 2).toLong()
            )
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _isStreaming.value = false
        _streamingStatus.value = null
        _streamingModelNote.value = null
        _streamingMessageId.value = null
        _streamingBuffer.value = ""
    }

    private suspend fun streamIntoModelMessage(
        conversationId: String,
        aiMessageId: String,
        aiSequenceIndex: Long,
        historyForModel: List<ChatMessageEntity>,
        userPrompt: String,
        attachedBitmap: Bitmap?,
        mode: NexoraMode,
        prefs: NexoraPreferencesData,
        totalMessageCount: Long
    ) {
        _isStreaming.value = true
        _streamingStatus.value = "Connecting to ${prefs.modelTier.label}..."
        _streamingModelNote.value = null
        _streamingMessageId.value = aiMessageId
        _streamingBuffer.value = ""

        val accumulated = StringBuilder()
        try {
            aiService.streamChatResponse(
                historyMessages = historyForModel,
                userPrompt = userPrompt,
                attachedBitmap = attachedBitmap,
                mode = mode,
                language = prefs.language,
                responseStyle = prefs.responseStyle,
                simpleExplanations = prefs.simpleExplanations,
                modelTier = prefs.modelTier
            ).collect { event ->
                when (event) {
                    is AiStreamEvent.Status -> {
                        _streamingStatus.value = event.message
                        _streamingModelNote.value = if (event.isFallback) {
                            "Fallback: ${event.modelTier.label}"
                        } else null
                    }
                    is AiStreamEvent.Chunk -> {
                        _streamingStatus.value = null
                        _streamingModelNote.value = if (event.isFallback) {
                            "${event.modelTier.label} (fallback)"
                        } else null
                        accumulated.clear()
                        accumulated.append(event.accumulatedText)
                        _streamingBuffer.value = event.accumulatedText
                    }
                }
            }

            val finalText = accumulated.toString().trim()
            if (finalText.isNotEmpty()) {
                persistNewMessage(
                    conversationId = conversationId,
                    messageId = aiMessageId,
                    role = "model",
                    content = finalText,
                    mode = mode,
                    hasError = false,
                    sequenceIndex = aiSequenceIndex
                )
                updateConversationMetadata(
                    conversationId = conversationId,
                    mode = mode,
                    language = prefs.language,
                    preview = finalText,
                    messageCount = totalMessageCount
                )
            }
        } catch (e: TimeoutCancellationException) {
            logViewModelError("Gemini streaming timed out", e)
            val errMsg = "Request timed out after 60 seconds. Check your connection and tap Retry."
            _chatError.value = errMsg
            persistNewMessage(
                conversationId = conversationId,
                messageId = aiMessageId,
                role = "model",
                content = errMsg,
                mode = mode,
                hasError = true,
                sequenceIndex = aiSequenceIndex
            )
        } catch (e: CancellationException) {
            // Save partial text if user tapped Stop Generation after receiving tokens
            val partial = accumulated.toString().trim()
            if (partial.isNotEmpty()) {
                withContext(NonCancellable) {
                    persistNewMessage(
                        conversationId = conversationId,
                        messageId = aiMessageId,
                        role = "model",
                        content = "$partial\n\n*[Generation stopped]*",
                        mode = mode,
                        hasError = false,
                        sequenceIndex = aiSequenceIndex
                    )
                    updateConversationMetadata(
                        conversationId = conversationId,
                        mode = mode,
                        language = prefs.language,
                        preview = partial,
                        messageCount = totalMessageCount
                    )
                }
            }
            throw e
        } catch (e: Exception) {
            logViewModelError("Gemini AI inference error", e)
            val friendly = friendlyAiErrorMessage(e)
            _chatError.value = friendly
            persistNewMessage(
                conversationId = conversationId,
                messageId = aiMessageId,
                role = "model",
                content = friendly,
                mode = mode,
                hasError = true,
                sequenceIndex = aiSequenceIndex
            )
        } finally {
            _isStreaming.value = false
            _streamingStatus.value = null
            _streamingModelNote.value = null
            _streamingMessageId.value = null
            _streamingBuffer.value = ""
        }
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        val clean = newTitle.trim().take(120)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            if (!currentUserId.isNullOrBlank()) {
                firestoreRepository.renameConversation(currentUserId, conversationId, clean)
                    .onFailure { err ->
                        _chatError.value = err.localizedMessage ?: "Failed to rename conversation."
                    }
            } else {
                _guestConversations.update { list ->
                    list.map { if (it.id == conversationId) it.copy(title = clean) else it }
                }
            }
        }
    }

    fun clearActiveConversation() {
        val convId = _activeConversationId.value ?: return
        stopGeneration()
        viewModelScope.launch {
            if (!currentUserId.isNullOrBlank()) {
                firestoreRepository.clearConversationMessages(currentUserId, convId)
                    .onSuccess {
                        val existing = conversations.value.firstOrNull { it.id == convId }
                        firestoreRepository.updateConversationActivity(
                            userId = currentUserId,
                            conversationId = convId,
                            title = existing?.title ?: "Cleared Session",
                            mode = _activeMode.value,
                            language = preferences.value.language,
                            lastMessagePreview = "",
                            messageCount = 0L
                        )
                    }
                    .onFailure { err ->
                        _chatError.value = err.localizedMessage ?: "Failed to clear conversation."
                    }
            } else {
                _guestMessagesMap.update { it + (convId to emptyList()) }
                _guestConversations.update { list ->
                    list.map {
                        if (it.id == convId) it.copy(lastMessagePreview = "", messageCount = 0L)
                        else it
                    }
                }
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        if (_activeConversationId.value == conversationId) {
            stopGeneration()
            _activeConversationId.value = null
        }
        viewModelScope.launch {
            if (!currentUserId.isNullOrBlank()) {
                firestoreRepository.deleteConversation(currentUserId, conversationId)
                    .onFailure { err ->
                        _chatError.value = err.localizedMessage ?: "Failed to delete conversation."
                    }
            } else {
                _guestConversations.update { list -> list.filterNot { it.id == conversationId } }
                _guestMessagesMap.update { it - conversationId }
            }
        }
    }

    fun clearAllHistory() {
        stopGeneration()
        _activeConversationId.value = null
        viewModelScope.launch {
            if (!currentUserId.isNullOrBlank()) {
                firestoreRepository.deleteAllUserData(currentUserId)
                    .onSuccess {
                        _statusBanner.value = "All cloud conversations permanently deleted."
                    }
                    .onFailure { err ->
                        _statusBanner.value = err.localizedMessage ?: "Failed to delete cloud history."
                    }
            } else {
                _guestConversations.value = emptyList()
                _guestMessagesMap.value = emptyMap()
                _statusBanner.value = "Temporary guest session cleared."
            }
        }
    }

    // --- Exhibition Mode ---
    fun setExhibitionMode(mode: NexoraMode) {
        _exhibitionState.update { it.copy(mode = mode) }
    }

    fun runExhibitionPrompt(prompt: String, modeOverride: NexoraMode? = null) {
        val clean = prompt.trim()
        if (clean.isEmpty()) return

        val now = System.currentTimeMillis()
        if (clean == lastExhibitionSubmittedPrompt &&
            (_exhibitionState.value.isStreaming || now - lastExhibitionSubmittedAtMs < DUPLICATE_SUBMIT_DEBOUNCE_MS)
        ) {
            return
        }
        lastExhibitionSubmittedPrompt = clean
        lastExhibitionSubmittedAtMs = now

        stopExhibitionGeneration()

        val selectedMode = modeOverride ?: _exhibitionState.value.mode
        val prefs = preferences.value

        _exhibitionState.value = ExhibitionUiState(
            prompt = clean,
            response = "",
            mode = selectedMode,
            isStreaming = true,
            statusText = "Connecting to ${prefs.modelTier.label}...",
            errorMessage = null
        )

        activeExhibitionJob = viewModelScope.launch {
            try {
                aiService.streamChatResponse(
                    historyMessages = emptyList(),
                    userPrompt = clean,
                    attachedBitmap = null,
                    mode = selectedMode,
                    language = prefs.language,
                    responseStyle = NexoraResponseStyle.BALANCED,
                    simpleExplanations = prefs.simpleExplanations,
                    modelTier = prefs.modelTier
                ).collect { event ->
                    when (event) {
                        is AiStreamEvent.Status -> {
                            _exhibitionState.update {
                                it.copy(
                                    isStreaming = true,
                                    statusText = event.message
                                )
                            }
                        }
                        is AiStreamEvent.Chunk -> {
                            _exhibitionState.update {
                                it.copy(
                                    response = event.accumulatedText,
                                    isStreaming = true,
                                    statusText = if (event.isFallback) "Fallback: ${event.modelTier.label}" else null
                                )
                            }
                        }
                    }
                }
                _exhibitionState.update { it.copy(isStreaming = false, statusText = null) }
            } catch (e: CancellationException) {
                _exhibitionState.update { it.copy(isStreaming = false, statusText = null) }
                throw e
            } catch (e: Exception) {
                logViewModelError("Exhibition generation error", e)
                _exhibitionState.update {
                    it.copy(
                        isStreaming = false,
                        statusText = null,
                        errorMessage = friendlyAiErrorMessage(e)
                    )
                }
            }
        }
    }

    fun retryExhibitionPrompt() {
        val current = _exhibitionState.value
        if (current.isStreaming || current.prompt.isBlank()) return
        lastExhibitionSubmittedAtMs = 0L
        runExhibitionPrompt(current.prompt, current.mode)
    }

    fun stopExhibitionGeneration() {
        activeExhibitionJob?.cancel()
        activeExhibitionJob = null
        _exhibitionState.update { it.copy(isStreaming = false, statusText = null) }
    }

    // --- Internal Persistence Helpers ---
    private suspend fun createConversationRecord(
        title: String,
        mode: NexoraMode,
        language: NexoraLanguage,
        preview: String
    ): String {
        val convId = generateSafeDocId("conv")
        if (!currentUserId.isNullOrBlank()) {
            firestoreRepository.createConversation(
                userId = currentUserId,
                conversationId = convId,
                title = title,
                mode = mode,
                language = language,
                lastMessagePreview = preview,
                messageCount = 0L
            ).onFailure { err ->
                _chatError.value = err.localizedMessage ?: "Failed to create cloud conversation."
            }
        } else {
            val now = Timestamp.now()
            val entity = ConversationEntity(
                id = convId,
                userId = "guest",
                title = title,
                mode = mode.name,
                language = language.name,
                lastMessagePreview = preview.take(240),
                messageCount = 0L,
                createdAt = now,
                updatedAt = now
            )
            _guestConversations.update { listOf(entity) + it }
        }
        return convId
    }

    private suspend fun persistNewMessage(
        conversationId: String,
        messageId: String,
        role: String,
        content: String,
        mode: NexoraMode,
        hasError: Boolean,
        sequenceIndex: Long,
        attachedImageHint: String? = null
    ) {
        if (!currentUserId.isNullOrBlank()) {
            firestoreRepository.createMessage(
                userId = currentUserId,
                conversationId = conversationId,
                messageId = messageId,
                role = role,
                content = content,
                mode = mode,
                hasError = hasError,
                sequenceIndex = sequenceIndex,
                attachedImageHint = attachedImageHint
            )
        } else {
            val now = Timestamp.now()
            val msg = ChatMessageEntity(
                id = messageId,
                userId = "guest",
                conversationId = conversationId,
                role = role,
                content = content,
                mode = mode.name,
                hasError = hasError,
                sequenceIndex = sequenceIndex,
                attachedImageHint = attachedImageHint,
                createdAt = now,
                updatedAt = now
            )
            _guestMessagesMap.update { map ->
                val current = map[conversationId].orEmpty()
                map + (conversationId to (current + msg))
            }
        }
    }

    private suspend fun deleteSingleMessage(conversationId: String, messageId: String) {
        if (!currentUserId.isNullOrBlank()) {
            firestoreRepository.deleteMessage(currentUserId, conversationId, messageId)
        } else {
            _guestMessagesMap.update { map ->
                val current = map[conversationId].orEmpty().filterNot { it.id == messageId }
                map + (conversationId to current)
            }
        }
    }

    private suspend fun updateConversationMetadata(
        conversationId: String,
        mode: NexoraMode,
        language: NexoraLanguage,
        preview: String,
        messageCount: Long
    ) {
        val currentTitle = conversations.value.firstOrNull { it.id == conversationId }?.title ?: "IT Session"
        val cleanPreview = preview.replace("\n", " ").trim().take(240)
        if (!currentUserId.isNullOrBlank()) {
            firestoreRepository.updateConversationActivity(
                userId = currentUserId,
                conversationId = conversationId,
                title = currentTitle,
                mode = mode,
                language = language,
                lastMessagePreview = cleanPreview,
                messageCount = messageCount
            )
        } else {
            val now = Timestamp.now()
            _guestConversations.update { list ->
                list.map {
                    if (it.id == conversationId) {
                        it.copy(
                            mode = mode.name,
                            language = language.name,
                            lastMessagePreview = cleanPreview,
                            messageCount = messageCount,
                            updatedAt = now
                        )
                    } else it
                }.sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
        }
    }

    private fun deriveConversationTitle(prompt: String): String {
        val cleaned = prompt.replace("\n", " ").trim()
        return if (cleaned.length <= 44) cleaned else cleaned.take(41).trimEnd() + "..."
    }

    private fun generateSafeDocId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    private fun rememberSessionBitmap(messageId: String, bitmap: Bitmap) {
        if (sessionBitmaps.size >= 10) {
            val oldestKey = sessionBitmaps.keys.firstOrNull()
            if (oldestKey != null) sessionBitmaps.remove(oldestKey)
        }
        sessionBitmaps[messageId] = bitmap
    }

    private fun friendlyAiErrorMessage(e: Exception): String {
        if (e is AiServiceException) return e.userMessage
        return NexoraAiService.classifyError(e).userMessage
    }

    private fun logViewModelError(message: String, e: Throwable) {
        try {
            Log.e(TAG, "$message | chain=${NexoraAiService.formatCausalChain(e)}", e)
        } catch (_: RuntimeException) {
            // Safe no-op in pure JVM tests
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeGenerationJob?.cancel()
        activeExhibitionJob?.cancel()
    }
}
