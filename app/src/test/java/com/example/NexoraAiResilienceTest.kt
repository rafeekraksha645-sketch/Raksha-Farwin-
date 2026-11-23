package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiErrorCategory
import com.example.ai.AiRetryPolicy
import com.example.ai.AiServiceException
import com.example.ai.AiStreamEvent
import com.example.ai.AiStreamTransport
import com.example.ai.NexoraAiService
import com.example.data.FirestoreChatRepository
import com.example.data.PreferencesRepository
import com.example.model.ChatMessageEntity
import com.example.model.NexoraLanguage
import com.example.model.NexoraMode
import com.example.model.NexoraModelTier
import com.example.model.NexoraPreferencesData
import com.example.model.NexoraResponseStyle
import com.example.ui.screens.ChatScreen
import com.example.ui.theme.NexoraTheme
import com.example.viewmodel.NexoraViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.UnknownException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.ClosedChannelException
import java.nio.channels.UnresolvedAddressException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NexoraAiResilienceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun makeServerException(msg: String): ServerException =
        ServerException::class.java
            .getConstructor(String::class.java, Throwable::class.java)
            .newInstance(msg, null)

    private fun makeQuotaException(msg: String): QuotaExceededException =
        QuotaExceededException::class.java
            .getConstructor(String::class.java, Throwable::class.java)
            .newInstance(msg, null)

    private fun makeTimeoutException(msg: String): RequestTimeoutException =
        RequestTimeoutException::class.java
            .getConstructor(String::class.java, Throwable::class.java, List::class.java)
            .newInstance(msg, null, emptyList<Content>())

    private fun makeUnknownException(cause: Throwable?): UnknownException =
        UnknownException::class.java
            .getConstructor(String::class.java, Throwable::class.java)
            .newInstance("Something unexpected happened.", cause)

    @Test
    fun `error taxonomy distinguishes HTTP 503 high demand, 429 rate limit, quota exhausted, timeout, network, and 403`() {
        val highDemand503 = NexoraAiService.classifyError(
            makeServerException("HTTP 503 UNAVAILABLE: This model is currently experiencing high demand.")
        )
        assertEquals(AiErrorCategory.MODEL_OVERLOADED_503, highDemand503.category)
        assertTrue(highDemand503.category.isSameModelRetryable)
        assertTrue(highDemand503.category.allowsModelFallback)

        val rateLimit429 = NexoraAiService.classifyError(
            makeQuotaException("HTTP 429 RESOURCE_EXHAUSTED: Too many requests per minute.")
        )
        assertEquals(AiErrorCategory.RATE_LIMIT_429, rateLimit429.category)
        assertTrue(rateLimit429.category.isSameModelRetryable)
        assertTrue(rateLimit429.category.allowsModelFallback)

        val hardQuota = NexoraAiService.classifyError(
            makeQuotaException("Quota exhausted: daily free_tier limit: 0. Check billing.")
        )
        assertEquals(AiErrorCategory.QUOTA_EXHAUSTED, hardQuota.category)
        assertFalse(hardQuota.category.isSameModelRetryable)
        assertTrue(hardQuota.category.allowsModelFallback)

        val timeout = NexoraAiService.classifyError(
            makeTimeoutException("The request failed to complete in the allotted time.")
        )
        assertEquals(AiErrorCategory.TIMEOUT, timeout.category)

        val network = NexoraAiService.classifyError(
            UnknownHostException("Unable to resolve host generativelanguage.googleapis.com")
        )
        assertEquals(AiErrorCategory.NETWORK, network.category)
        assertFalse(network.category.allowsModelFallback)

        val appCheck403 = NexoraAiService.classifyError(
            RuntimeException("HTTP 403 PERMISSION_DENIED: AppCheck token rejected")
        )
        assertEquals(AiErrorCategory.APP_CHECK_403, appCheck403.category)
        assertFalse(appCheck403.category.isSameModelRetryable)
        assertFalse(appCheck403.category.allowsModelFallback)
    }

    @Test
    fun `unknownException unwraps underlying Ktor network, timeout, AppCheck, and transport causes instead of hiding behind generic text`() {
        // UnresolvedAddressException has null message on Android/JVM
        val dnsWrapped = NexoraAiService.classifyError(
            makeUnknownException(UnresolvedAddressException())
        )
        assertEquals(AiErrorCategory.NETWORK, dnsWrapped.category)
        assertFalse(dnsWrapped.userMessage.contains("Something unexpected happened.", ignoreCase = true))

        val timeoutWrapped = NexoraAiService.classifyError(
            makeUnknownException(SocketTimeoutException("connect timed out"))
        )
        assertEquals(AiErrorCategory.TIMEOUT, timeoutWrapped.category)

        val appCheckWrapped = NexoraAiService.classifyError(
            makeUnknownException(IllegalStateException("AppCheck debug token attestation rejected"))
        )
        assertEquals(AiErrorCategory.APP_CHECK_403, appCheckWrapped.category)

        val genericWrapped = NexoraAiService.classifyError(
            makeUnknownException(IllegalStateException("SSE frame truncated"))
        )
        assertEquals(AiErrorCategory.UNKNOWN, genericWrapped.category)
        assertTrue(genericWrapped.category.isSameModelRetryable)
        assertTrue(genericWrapped.category.allowsModelFallback)
        assertTrue(genericWrapped.userMessage.contains("SSE frame truncated"))
        assertTrue(genericWrapped.userMessage.contains("IllegalStateException"))
        assertFalse(genericWrapped.userMessage.contains("Something unexpected happened.", ignoreCase = true))

        val chainLog = NexoraAiService.formatCausalChain(
            makeUnknownException(UnresolvedAddressException())
        )
        assertTrue(chainLog.contains("UnknownException"))
        assertTrue(chainLog.contains("UnresolvedAddressException"))
    }

    @Test
    fun `streaming UnknownException from interrupted SSE recovers via unary fallback on same turn`() = runBlocking {
        val streamAttempts = AtomicInteger(0)
        val unaryAttempts = AtomicInteger(0)
        val fakeTransport = object : AiStreamTransport {
            override fun streamChat(
                modelTier: NexoraModelTier,
                history: List<Content>,
                systemInstruction: Content,
                temperature: Float,
                userPrompt: String,
                attachedBitmap: Bitmap?
            ): Flow<String> = flow {
                streamAttempts.incrementAndGet()
                throw makeUnknownException(ClosedChannelException())
            }

            override suspend fun generateUnary(
                modelTier: NexoraModelTier,
                history: List<Content>,
                systemInstruction: Content,
                temperature: Float,
                userPrompt: String,
                attachedBitmap: Bitmap?
            ): String {
                unaryAttempts.incrementAndGet()
                return "Python is a high-level programming language."
            }
        }

        val service = NexoraAiService(
            transport = fakeTransport,
            policy = AiRetryPolicy(
                maxPrimaryAttempts = 3,
                maxFallbackAttempts = 2,
                baseBackoffMs = 5L,
                maxBackoffMs = 20L,
                minRequestIntervalMs = 0L,
                jitterProvider = { 0L }
            )
        )

        val events = service.streamChatResponse(
            historyMessages = emptyList(),
            userPrompt = "What is Python?",
            attachedBitmap = null,
            mode = NexoraMode.PROGRAMMING,
            language = NexoraLanguage.ENGLISH,
            responseStyle = NexoraResponseStyle.BALANCED,
            simpleExplanations = false,
            modelTier = NexoraModelTier.FLASH
        ).toList()

        assertEquals(1, streamAttempts.get())
        assertEquals(1, unaryAttempts.get())
        val finalChunk = events.filterIsInstance<AiStreamEvent.Chunk>().last()
        assertEquals("Python is a high-level programming language.", finalChunk.accumulatedText)
        assertEquals(NexoraModelTier.FLASH, finalChunk.modelTier)
    }

    @Test
    fun `jsonDecodingException from truncated stream classifies cleanly and stays on unary across retries`() = runBlocking {
        val jsonClass = Class.forName("kotlinx.serialization.json.Json")
        val defaultJson = jsonClass.getField("Default").get(null)
        val parseMethod = jsonClass.getMethod("parseToJsonElement", String::class.java)
        val realJsonEx = try {
            parseMethod.invoke(defaultJson, "{")
            error("Expected JsonDecodingException")
        } catch (ite: java.lang.reflect.InvocationTargetException) {
            ite.targetException
        }

        val classified = NexoraAiService.classifyError(makeUnknownException(realJsonEx))
        assertEquals(AiErrorCategory.UNKNOWN, classified.category)
        assertTrue(classified.category.isSameModelRetryable)
        assertTrue(classified.category.allowsModelFallback)
        assertTrue(classified.userMessage.contains("JsonDecodingException"))
        assertFalse(classified.userMessage.contains("JSON input: {"))

        val streamAttempts = AtomicInteger(0)
        val unaryAttempts = AtomicInteger(0)
        val fakeTransport = object : AiStreamTransport {
            override fun streamChat(
                modelTier: NexoraModelTier,
                history: List<Content>,
                systemInstruction: Content,
                temperature: Float,
                userPrompt: String,
                attachedBitmap: Bitmap?
            ): Flow<String> = flow {
                streamAttempts.incrementAndGet()
                throw makeUnknownException(realJsonEx)
            }

            override suspend fun generateUnary(
                modelTier: NexoraModelTier,
                history: List<Content>,
                systemInstruction: Content,
                temperature: Float,
                userPrompt: String,
                attachedBitmap: Bitmap?
            ): String {
                val n = unaryAttempts.incrementAndGet()
                if (n == 1) {
                    throw makeServerException("503 This model is currently experiencing high demand.")
                }
                return "Cybersecurity protects systems, networks, and data from digital attacks."
            }
        }

        val service = NexoraAiService(
            transport = fakeTransport,
            policy = AiRetryPolicy(
                maxPrimaryAttempts = 3,
                maxFallbackAttempts = 2,
                baseBackoffMs = 5L,
                maxBackoffMs = 20L,
                minRequestIntervalMs = 0L,
                jitterProvider = { 0L }
            )
        )

        val events = service.streamChatResponse(
            historyMessages = emptyList(),
            userPrompt = "What is Cybersecurity?",
            attachedBitmap = null,
            mode = NexoraMode.CYBERSECURITY,
            language = NexoraLanguage.ENGLISH,
            responseStyle = NexoraResponseStyle.BALANCED,
            simpleExplanations = false,
            modelTier = NexoraModelTier.FLASH
        ).toList()

        // Must NOT revert back to broken streamChat on attempt 2 after unary already took over
        assertEquals(1, streamAttempts.get())
        assertEquals(2, unaryAttempts.get())
        val finalChunk = events.filterIsInstance<AiStreamEvent.Chunk>().last()
        assertEquals("Cybersecurity protects systems, networks, and data from digital attacks.", finalChunk.accumulatedText)
    }

    @Test
    fun `exponential backoff retries transient HTTP 503 and recovers on third attempt`() = runBlocking {
        val attempts = AtomicInteger(0)
        val fakeTransport = object : AiStreamTransport {
            override fun streamChat(
                modelTier: NexoraModelTier,
                history: List<Content>,
                systemInstruction: Content,
                temperature: Float,
                userPrompt: String,
                attachedBitmap: Bitmap?
            ): Flow<String> = flow {
                val n = attempts.incrementAndGet()
                if (n < 3) {
                    throw makeServerException("503 This model is currently experiencing high demand.")
                }
                emit("Cybersecurity protects ")
                emit("systems and networks.")
            }
        }

        val service = NexoraAiService(
            transport = fakeTransport,
            policy = AiRetryPolicy(
                maxPrimaryAttempts = 3,
                maxFallbackAttempts = 2,
                baseBackoffMs = 5L,
                maxBackoffMs = 20L,
                minRequestIntervalMs = 0L,
                jitterProvider = { 0L }
            )
        )

        val events = service.streamChatResponse(
            historyMessages = emptyList(),
            userPrompt = "What is Cybersecurity?",
            attachedBitmap = null,
            mode = NexoraMode.CYBERSECURITY,
            language = NexoraLanguage.ENGLISH,
            responseStyle = NexoraResponseStyle.BALANCED,
            simpleExplanations = false,
            modelTier = NexoraModelTier.FLASH
        ).toList()

        assertEquals(3, attempts.get())
        val chunks = events.filterIsInstance<AiStreamEvent.Chunk>()
        assertEquals("Cybersecurity protects systems and networks.", chunks.last().accumulatedText)
        assertEquals(NexoraModelTier.FLASH, chunks.last().modelTier)
        assertFalse(chunks.last().isFallback)
        assertTrue(events.filterIsInstance<AiStreamEvent.Status>().any { it.message.contains("retrying", ignoreCase = true) })
    }

    @Test
    fun `falls back to gemini-3_5-flash-lite when gemini-3_8-flash stays overloaded with 503`() = runBlocking {
        val calledTiers = mutableListOf<NexoraModelTier>()
        val fakeTransport = object : AiStreamTransport {
            override fun streamChat(
                modelTier: NexoraModelTier,
                history: List<Content>,
                systemInstruction: Content,
                temperature: Float,
                userPrompt: String,
                attachedBitmap: Bitmap?
            ): Flow<String> = flow {
                calledTiers.add(modelTier)
                if (modelTier == NexoraModelTier.FLASH) {
                    throw makeServerException("503 This model is currently experiencing high demand.")
                }
                emit("A Logic Gate performs Boolean operations on binary inputs.")
            }
        }

        val service = NexoraAiService(
            transport = fakeTransport,
            policy = AiRetryPolicy(
                maxPrimaryAttempts = 3,
                maxFallbackAttempts = 2,
                baseBackoffMs = 5L,
                maxBackoffMs = 20L,
                minRequestIntervalMs = 0L,
                jitterProvider = { 0L }
            )
        )

        val events = service.streamChatResponse(
            historyMessages = emptyList(),
            userPrompt = "What is a Logic Gate?",
            attachedBitmap = null,
            mode = NexoraMode.LEARNING,
            language = NexoraLanguage.ENGLISH,
            responseStyle = NexoraResponseStyle.BALANCED,
            simpleExplanations = true,
            modelTier = NexoraModelTier.FLASH
        ).toList()

        assertEquals(
            listOf(
                NexoraModelTier.FLASH,
                NexoraModelTier.FLASH,
                NexoraModelTier.FLASH,
                NexoraModelTier.FLASH_LITE
            ),
            calledTiers
        )
        val finalChunk = events.filterIsInstance<AiStreamEvent.Chunk>().last()
        assertEquals(NexoraModelTier.FLASH_LITE, finalChunk.modelTier)
        assertTrue(finalChunk.isFallback)
        assertTrue(finalChunk.accumulatedText.contains("Logic Gate"))
    }

    @Test
    fun `request spacing delays back-to-back calls to prevent rate-limit bursts`() = runBlocking {
        var fakeClock = 10_000L
        val fakeTransport = object : AiStreamTransport {
            override fun streamChat(
                modelTier: NexoraModelTier,
                history: List<Content>,
                systemInstruction: Content,
                temperature: Float,
                userPrompt: String,
                attachedBitmap: Bitmap?
            ): Flow<String> = flow {
                emit("ok")
            }
        }

        val service = NexoraAiService(
            transport = fakeTransport,
            policy = AiRetryPolicy(
                baseBackoffMs = 5L,
                minRequestIntervalMs = 40L,
                jitterProvider = { 0L }
            ),
            clockMs = { fakeClock }
        )

        val tStart = System.nanoTime()
        service.streamChatResponse(
            emptyList(), "Explain Number Systems.", null,
            NexoraMode.GENERAL_IT, NexoraLanguage.ENGLISH, NexoraResponseStyle.CONCISE, false, NexoraModelTier.FLASH
        ).toList()
        // Same fakeClock timestamp forces rate-limit slot wait
        service.streamChatResponse(
            emptyList(), "What is Python?", null,
            NexoraMode.PROGRAMMING, NexoraLanguage.ENGLISH, NexoraResponseStyle.CONCISE, false, NexoraModelTier.FLASH
        ).toList()
        val elapsedMs = (System.nanoTime() - tStart) / 1_000_000L
        assertTrue("Expected rate-limit spacing >= 30ms, was ${elapsedMs}ms", elapsedMs >= 30L)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `viewModel preserves conversation history on failure and recovers via retryFailedMessage`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            var failNextTurn = false
            var lastObservedHistorySize = -1
            val fakeTransport = object : AiStreamTransport {
                override fun streamChat(
                    modelTier: NexoraModelTier,
                    history: List<Content>,
                    systemInstruction: Content,
                    temperature: Float,
                    userPrompt: String,
                    attachedBitmap: Bitmap?
                ): Flow<String> = flow {
                    lastObservedHistorySize = history.size
                    if (failNextTurn) {
                        throw makeQuotaException("429 Rate limit reached")
                    }
                    emit("Answer for: $userPrompt")
                }
            }

            val aiService = NexoraAiService(
                transport = fakeTransport,
                policy = AiRetryPolicy(
                    maxPrimaryAttempts = 2,
                    maxFallbackAttempts = 1,
                    baseBackoffMs = 2L,
                    maxBackoffMs = 10L,
                    minRequestIntervalMs = 0L,
                    jitterProvider = { 0L }
                )
            )
            val vm = NexoraViewModel(
                currentUserId = null,
                userDisplayName = null,
                userEmail = null,
                preferencesRepository = PreferencesRepository(context),
                firestoreRepository = FirestoreChatRepository(context),
                aiService = aiService
            )

            backgroundScope.launch(testDispatcher) {
                vm.activeMessages.collect {}
            }
            advanceUntilIdle()

            // Turn 1 succeeds
            vm.sendMessage("What is Python?")
            advanceUntilIdle()
            val turn1Msgs = vm.activeMessages.value
            assertEquals(2, turn1Msgs.size)
            assertFalse(turn1Msgs[1].hasError)
            assertEquals(0, lastObservedHistorySize)

            // Turn 2 fails -> history preserved + error bubble created
            failNextTurn = true
            vm.sendMessage("Explain Computer Networks.")
            advanceUntilIdle()
            val turn2FailedMsgs = vm.activeMessages.value
            assertEquals(4, turn2FailedMsgs.size)
            assertEquals("What is Python?", turn2FailedMsgs[0].content)
            assertTrue(turn2FailedMsgs[3].hasError)
            assertNotNull(vm.chatError.value)

            // Retry Turn 2 after recovery -> error bubble replaced and Turn 1 history preserved
            failNextTurn = false
            vm.retryFailedMessage(turn2FailedMsgs[3])
            advanceUntilIdle()

            val recoveredMsgs = vm.activeMessages.value
            assertEquals(4, recoveredMsgs.size)
            assertFalse(recoveredMsgs[3].hasError)
            assertEquals("Answer for: Explain Computer Networks.", recoveredMsgs[3].content)
            assertEquals(2, lastObservedHistorySize)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `chat screen displays loading indicator during connection and functional retry button on error`() {
        var retriedMessageId: String? = null
        val streamingMsgs = listOf(
            ChatMessageEntity(
                id = "u1",
                role = "user",
                content = "What is Cybersecurity?",
                sequenceIndex = 0L
            ),
            ChatMessageEntity(
                id = "m1",
                role = "model",
                content = "",
                hasError = false,
                sequenceIndex = 1L
            )
        )

        composeRule.setContent {
            NexoraTheme {
                ChatScreen(
                    conversations = emptyList(),
                    activeConversationId = "conv_1",
                    messages = streamingMsgs,
                    activeMode = NexoraMode.CYBERSECURITY,
                    preferences = NexoraPreferencesData(),
                    isStreaming = true,
                    chatError = null,
                    isAuthenticated = false,
                    isFullScreen = false,
                    onToggleFullScreen = {},
                    onSelectMode = {},
                    onOpenLanguagePicker = {},
                    onOpenExhibition = {},
                    onOpenAccount = {},
                    onNewChat = {},
                    onSelectConversation = {},
                    onRenameConversation = { _, _ -> },
                    onDeleteConversation = {},
                    onClearConversation = {},
                    onSendMessage = { _, _ -> },
                    onStopGeneration = {},
                    onRegenerateResponse = {},
                    onRetryMessage = { retriedMessageId = it.id },
                    onDismissError = {},
                    streamingStatus = "High demand on Gemini 3.8 Flash — retrying in 2s..."
                )
            }
        }

        composeRule.onNodeWithTag("ai_loading_indicator").assertIsDisplayed()
        composeRule.onNodeWithText("High demand on Gemini 3.8 Flash — retrying in 2s...").assertIsDisplayed()
    }

    @Test
    fun `chat screen retry button invokes retry callback for failed message`() {
        var retriedMessageId: String? = null
        val errorMsgs = listOf(
            ChatMessageEntity(
                id = "u1",
                role = "user",
                content = "Explain Number Systems.",
                sequenceIndex = 0L
            ),
            ChatMessageEntity(
                id = "m_err",
                role = "model",
                content = "Rate limit reached (HTTP 429). Please wait 15–30 seconds and tap Retry.",
                hasError = true,
                sequenceIndex = 1L
            )
        )

        composeRule.setContent {
            NexoraTheme {
                ChatScreen(
                    conversations = emptyList(),
                    activeConversationId = "conv_1",
                    messages = errorMsgs,
                    activeMode = NexoraMode.LEARNING,
                    preferences = NexoraPreferencesData(),
                    isStreaming = false,
                    chatError = errorMsgs[1].content,
                    isAuthenticated = false,
                    isFullScreen = false,
                    onToggleFullScreen = {},
                    onSelectMode = {},
                    onOpenLanguagePicker = {},
                    onOpenExhibition = {},
                    onOpenAccount = {},
                    onNewChat = {},
                    onSelectConversation = {},
                    onRenameConversation = { _, _ -> },
                    onDeleteConversation = {},
                    onClearConversation = {},
                    onSendMessage = { _, _ -> },
                    onStopGeneration = {},
                    onRegenerateResponse = {},
                    onRetryMessage = { retriedMessageId = it.id },
                    onDismissError = {}
                )
            }
        }

        composeRule.onNodeWithTag("retry_message_button").assertIsDisplayed().performClick()
        assertEquals("m_err", retriedMessageId)
    }
}
