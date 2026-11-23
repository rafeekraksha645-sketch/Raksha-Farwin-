package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CoPresent
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ChatMessageEntity
import com.example.model.ConversationEntity
import com.example.model.NexoraLanguage
import com.example.model.NexoraMode
import com.example.model.NexoraPreferencesData
import com.example.ui.components.GlassCard
import com.example.ui.components.LanguagePill
import com.example.ui.components.ModeChipSelector
import com.example.ui.components.NexoraMarkdownText
import com.example.ui.components.NexoraVectorEmblem
import com.example.ui.theme.LocalNexoraColors
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    conversations: List<ConversationEntity>,
    activeConversationId: String?,
    messages: List<ChatMessageEntity>,
    activeMode: NexoraMode,
    preferences: NexoraPreferencesData,
    isStreaming: Boolean,
    chatError: String?,
    isAuthenticated: Boolean,
    isFullScreen: Boolean,
    onToggleFullScreen: (Boolean) -> Unit,
    onSelectMode: (NexoraMode) -> Unit,
    onOpenLanguagePicker: () -> Unit,
    onOpenExhibition: () -> Unit,
    onOpenAccount: () -> Unit,
    onNewChat: () -> Unit,
    onSelectConversation: (ConversationEntity) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onClearConversation: () -> Unit,
    onSendMessage: (String, Bitmap?) -> Unit,
    onStopGeneration: () -> Unit,
    onRegenerateResponse: () -> Unit,
    onRetryMessage: (ChatMessageEntity) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    streamingStatus: String? = null,
    streamingModelNote: String? = null
) {
    if (isFullScreen) {
        BackHandler { onToggleFullScreen(false) }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var renameTarget by remember { mutableStateOf<ConversationEntity?>(null) }

    val activeConversation = remember(conversations, activeConversationId) {
        conversations.firstOrNull { it.id == activeConversationId }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp && !isFullScreen

        val mainChatContent: @Composable () -> Unit = {
            ChatWorkspacePane(
                activeConversation = activeConversation,
                messages = messages,
                activeMode = activeMode,
                language = preferences.language,
                isStreaming = isStreaming,
                streamingStatus = streamingStatus,
                streamingModelNote = streamingModelNote,
                chatError = chatError,
                isFullScreen = isFullScreen,
                showDrawerButton = !isWideScreen && !isFullScreen,
                onOpenHistoryDrawer = { scope.launch { drawerState.open() } },
                onToggleFullScreen = onToggleFullScreen,
                onSelectMode = onSelectMode,
                onOpenLanguagePicker = onOpenLanguagePicker,
                onOpenExhibition = onOpenExhibition,
                onNewChat = onNewChat,
                onRenameActive = { activeConversation?.let { renameTarget = it } },
                onClearConversation = onClearConversation,
                onSendMessage = onSendMessage,
                onStopGeneration = onStopGeneration,
                onRegenerateResponse = onRegenerateResponse,
                onRetryMessage = onRetryMessage,
                onDismissError = onDismissError
            )
        }

        if (isWideScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                ) {
                    ConversationSidebarContent(
                        conversations = conversations,
                        activeConversationId = activeConversationId,
                        isAuthenticated = isAuthenticated,
                        onNewChat = onNewChat,
                        onSelectConversation = onSelectConversation,
                        onRenameConversation = { renameTarget = it },
                        onDeleteConversation = onDeleteConversation,
                        onOpenAccount = onOpenAccount
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Box(modifier = Modifier.weight(1f)) {
                    mainChatContent()
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = !isFullScreen,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.width(300.dp)
                    ) {
                        ConversationSidebarContent(
                            conversations = conversations,
                            activeConversationId = activeConversationId,
                            isAuthenticated = isAuthenticated,
                            onNewChat = {
                                onNewChat()
                                scope.launch { drawerState.close() }
                            },
                            onSelectConversation = { conv ->
                                onSelectConversation(conv)
                                scope.launch { drawerState.close() }
                            },
                            onRenameConversation = { renameTarget = it },
                            onDeleteConversation = onDeleteConversation,
                            onOpenAccount = {
                                scope.launch { drawerState.close() }
                                onOpenAccount()
                            }
                        )
                    }
                }
            ) {
                mainChatContent()
            }
        }
    }

    renameTarget?.let { conv ->
        var editedTitle by remember(conv.id) { mutableStateOf(conv.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = { Text("Conversation Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameConversation(conv.id, editedTitle)
                        renameTarget = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ConversationSidebarContent(
    conversations: List<ConversationEntity>,
    activeConversationId: String?,
    isAuthenticated: Boolean,
    onNewChat: () -> Unit,
    onSelectConversation: (ConversationEntity) -> Unit,
    onRenameConversation: (ConversationEntity) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onOpenAccount: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onNewChat,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("new_chat_button")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "New chat")
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Chat")
        }

        if (!isAuthenticated) {
            Surface(
                onClick = onOpenAccount,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Temporary Guest Session",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tap to Sign In with Google for cloud history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text(
            text = if (isAuthenticated) "CLOUD CONVERSATIONS" else "SESSION HISTORY",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "No conversations yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    val isSelected = conv.id == activeConversationId
                    Surface(
                        onClick = { onSelectConversation(conv) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        border = if (isSelected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_item_${conv.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conv.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = conv.nexoraMode.shortTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { onRenameConversation(conv) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Rename conversation",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onDeleteConversation(conv.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete conversation",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatWorkspacePane(
    activeConversation: ConversationEntity?,
    messages: List<ChatMessageEntity>,
    activeMode: NexoraMode,
    language: NexoraLanguage,
    isStreaming: Boolean,
    streamingStatus: String?,
    streamingModelNote: String?,
    chatError: String?,
    isFullScreen: Boolean,
    showDrawerButton: Boolean,
    onOpenHistoryDrawer: () -> Unit,
    onToggleFullScreen: (Boolean) -> Unit,
    onSelectMode: (NexoraMode) -> Unit,
    onOpenLanguagePicker: () -> Unit,
    onOpenExhibition: () -> Unit,
    onNewChat: () -> Unit,
    onRenameActive: () -> Unit,
    onClearConversation: () -> Unit,
    onSendMessage: (String, Bitmap?) -> Unit,
    onStopGeneration: () -> Unit,
    onRegenerateResponse: () -> Unit,
    onRetryMessage: (ChatMessageEntity) -> Unit,
    onDismissError: () -> Unit
) {
    val context = LocalContext.current
    var promptText by remember { mutableStateOf("") }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            attachedBitmap = decodeUriToBitmap(context, uri)
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Chat Toolbar (compact in Full-Screen Mode, rich normally)
        if (isFullScreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NexoraVectorEmblem(size = 24.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = activeConversation?.title ?: "NEXORA AI · ${activeMode.shortTitle}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    onClick = { onToggleFullScreen(false) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("exit_fullscreen_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FullscreenExit,
                            contentDescription = "Exit full screen",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Exit Full Screen",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showDrawerButton) {
                        IconButton(
                            onClick = onOpenHistoryDrawer,
                            modifier = Modifier.testTag("chat_history_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = "Open chat history"
                            )
                        }
                    }
                    Text(
                        text = activeConversation?.title ?: "New IT Session",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .let { mod ->
                                if (activeConversation != null) mod.clickable(onClick = onRenameActive)
                                else mod
                            }
                    )
                    LanguagePill(
                        currentLanguage = language,
                        onClick = onOpenLanguagePicker
                    )
                    if (activeConversation != null && messages.isNotEmpty()) {
                        IconButton(
                            onClick = onClearConversation,
                            modifier = Modifier.testTag("clear_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = "Clear conversation"
                            )
                        }
                    }
                    IconButton(
                        onClick = onNewChat,
                        modifier = Modifier.testTag("toolbar_new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Start new chat"
                        )
                    }
                    IconButton(
                        onClick = { onToggleFullScreen(true) },
                        modifier = Modifier.testTag("enter_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = "Full-screen chat mode"
                        )
                    }
                    IconButton(
                        onClick = onOpenExhibition,
                        modifier = Modifier.testTag("chat_exhibition_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CoPresent,
                            contentDescription = "Exhibition presentation mode"
                        )
                    }
                }

                ModeChipSelector(
                    selectedMode = activeMode,
                    onSelectMode = onSelectMode
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        // Error banner
        if (!chatError.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = chatError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismissError, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss error",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Message List or Empty Welcome State
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 560.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NexoraVectorEmblem(size = 56.dp)
                    Text(
                        text = "NEXORA AI · ${activeMode.title}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = activeMode.tagline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    GlassCard(
                        onClick = { onSendMessage(activeMode.samplePrompt, null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Suggested ${activeMode.shortTitle} Prompt",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activeMode.samplePrompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            val lastModelId = remember(messages) {
                messages.lastOrNull { !it.isUser && !it.hasError }?.id
            }
            val lastMessageId = messages.lastOrNull()?.id
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val streamingThisItem = isStreaming && msg.id == lastMessageId && !msg.isUser
                    ChatMessageItem(
                        message = msg,
                        isLatestModelMessage = msg.id == lastModelId,
                        isCurrentlyStreaming = streamingThisItem,
                        streamingStatus = if (streamingThisItem) streamingStatus else null,
                        streamingModelNote = if (streamingThisItem) streamingModelNote else null,
                        onRegenerate = onRegenerateResponse,
                        onRetry = { onRetryMessage(msg) }
                    )
                }
            }
        }

        // Attached Bitmap Preview + Bottom Message Composer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            attachedBitmap?.let { bmp ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(6.dp)
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Attached screenshot preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Screenshot attached",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { attachedBitmap = null },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove image")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("attach_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = "Attach code or error screenshot",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    placeholder = {
                        Text(
                            text = language.promptHint,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )

                if (isStreaming) {
                    IconButton(
                        onClick = onStopGeneration,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("stop_generation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Stop AI generation"
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            val text = promptText.trim()
                            val bmp = attachedBitmap
                            if (text.isNotEmpty() || bmp != null) {
                                promptText = ""
                                attachedBitmap = null
                                onSendMessage(text, bmp)
                            }
                        },
                        enabled = promptText.isNotBlank() || attachedBitmap != null,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send message"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessageEntity,
    isLatestModelMessage: Boolean,
    isCurrentlyStreaming: Boolean,
    streamingStatus: String?,
    streamingModelNote: String?,
    onRegenerate: () -> Unit,
    onRetry: () -> Unit
) {
    val nexoraColors = LocalNexoraColors.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1800L)
            copied = false
        }
    }

    if (message.isUser) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .background(nexoraColors.userBubbleBg)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                    )
                    .padding(14.dp)
            ) {
                if (!message.attachedImageHint.isNullOrBlank()) {
                    Text(
                        text = "[${message.attachedImageHint}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .background(
                    if (message.hasError) MaterialTheme.colorScheme.errorContainer
                    else nexoraColors.aiBubbleBg
                )
                .border(
                    width = 1.dp,
                    color = if (message.hasError) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    else nexoraColors.glassBorder,
                    shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)
                )
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    NexoraVectorEmblem(size = 20.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = buildString {
                            append("NEXORA AI · ${message.nexoraMode.shortTitle}")
                            if (!streamingModelNote.isNullOrBlank()) {
                                append(" · $streamingModelNote")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.hasError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isCurrentlyStreaming) {
                    StreamingPulseIndicator()
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (message.hasError) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag("retry_message_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Retry failed message",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
            } else if (isCurrentlyStreaming && message.content.isBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("ai_loading_indicator")
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = streamingStatus ?: "Connecting to NEXORA AI...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                NexoraMarkdownText(
                    markdown = message.content,
                    textColor = MaterialTheme.colorScheme.onSurface
                )

                if (!isCurrentlyStreaming) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(message.content))
                                copied = true
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("copy_response_button")
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                contentDescription = "Copy response",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (copied) "Copied" else "Copy")
                        }

                        if (isLatestModelMessage) {
                            TextButton(
                                onClick = onRegenerate,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("regenerate_response_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Regenerate response",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Regenerate")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingPulseIndicator() {
    val transition = rememberInfiniteTransition(label = "streaming_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.alpha(alpha)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "Generating",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun decodeUriToBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        null
    }
}
