package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ai.NexoraAiService
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.FirestoreChatRepository
import com.example.data.PreferencesRepository
import com.example.firebase.configureAppCheck
import com.example.model.NexoraMode
import com.example.model.NexoraThemeMode
import com.example.ui.components.AccountStatusDialog
import com.example.ui.components.LanguagePickerDialog
import com.example.ui.components.LanguagePill
import com.example.ui.components.NexoraBrandMark
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ExhibitionScreen
import com.example.ui.screens.ExploreItScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InfoDetailScreen
import com.example.ui.screens.InfoSection
import com.example.ui.screens.MoreHubScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.NexoraTheme
import com.example.viewmodel.NexoraViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object NexoraRoutes {
    const val HOME = "home"
    const val ASSISTANT = "assistant"
    const val EXPLORE = "explore"
    const val MORE = "more"
    const val SETTINGS = "settings"
    const val EXHIBITION = "exhibition"
    const val INFO = "info"
}

private enum class TopLevelTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME(NexoraRoutes.HOME, "Home", Icons.Rounded.Home),
    ASSISTANT(NexoraRoutes.ASSISTANT, "Assistant", Icons.AutoMirrored.Rounded.Chat),
    EXPLORE(NexoraRoutes.EXPLORE, "Explore IT", Icons.Rounded.Explore),
    MORE(NexoraRoutes.MORE, "More", Icons.Rounded.Apps)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            NexoraApplicationShell()
        }
    }
}

@Composable
fun NexoraApplicationShell() {
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()

    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = scope
        )
    }

    val userKey = currentUser?.uid ?: "guest"
    key(userKey) {
        val viewModel: NexoraViewModel = viewModel(
            key = userKey,
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY]) as Application
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    NexoraViewModel(
                        currentUserId = currentUser?.uid,
                        userDisplayName = currentUser?.displayName,
                        userEmail = currentUser?.email,
                        preferencesRepository = PreferencesRepository(app),
                        firestoreRepository = FirestoreChatRepository(db),
                        aiService = NexoraAiService()
                    )
                }
            }
        )

        val preferences by viewModel.preferences.collectAsStateWithLifecycle()

        NexoraTheme(themeMode = preferences.themeMode) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                NexoraMainContent(
                    viewModel = viewModel,
                    onSignOut = {
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope,
                            onBeforeSignOut = { viewModel.stopGeneration() }
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NexoraMainContent(
    viewModel: NexoraViewModel,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: NexoraRoutes.HOME

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val activeMode by viewModel.activeMode.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val isFullScreenChat by viewModel.isFullScreenChat.collectAsStateWithLifecycle()
    val exhibitionState by viewModel.exhibitionState.collectAsStateWithLifecycle()

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var exploreInitialCategory by remember { mutableStateOf<String?>(null) }

    val hideChrome = (currentRoute == NexoraRoutes.ASSISTANT && isFullScreenChat) ||
        currentRoute == NexoraRoutes.EXHIBITION

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun launchPromptInAssistant(prompt: String, mode: NexoraMode) {
        viewModel.startNewChat(mode)
        viewModel.sendMessage(promptText = prompt, attachedBitmap = null, modeOverride = mode)
        navigateTopLevel(NexoraRoutes.ASSISTANT)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 840.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (!hideChrome) {
                    TopAppBar(
                        title = {
                            NexoraBrandMark(
                                iconSize = 30.dp,
                                showImageEmblem = true
                            )
                        },
                        actions = {
                            LanguagePill(
                                currentLanguage = preferences.language,
                                onClick = { showLanguageDialog = true }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    val nextTheme = when (preferences.themeMode) {
                                        NexoraThemeMode.DARK -> NexoraThemeMode.LIGHT
                                        NexoraThemeMode.LIGHT -> NexoraThemeMode.SYSTEM
                                        NexoraThemeMode.SYSTEM -> NexoraThemeMode.DARK
                                    }
                                    viewModel.updateThemeMode(nextTheme)
                                },
                                modifier = Modifier.testTag("theme_cycle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Brightness6,
                                    contentDescription = "Cycle theme mode"
                                )
                            }
                            IconButton(
                                onClick = { showAccountDialog = true },
                                modifier = Modifier.testTag("account_dialog_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountCircle,
                                    contentDescription = if (viewModel.isAuthenticated) "Account settings" else "Sign in",
                                    tint = if (viewModel.isAuthenticated) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            },
            bottomBar = {
                if (!hideChrome && !useNavRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        TopLevelTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route ||
                                (tab == TopLevelTab.MORE && (currentRoute == NexoraRoutes.SETTINGS || currentRoute.startsWith(NexoraRoutes.INFO)))
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (tab == TopLevelTab.EXPLORE) exploreInitialCategory = null
                                    navigateTopLevel(tab.route)
                                },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!hideChrome && useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(0.1f))
                        TopLevelTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route ||
                                (tab == TopLevelTab.MORE && (currentRoute == NexoraRoutes.SETTINGS || currentRoute.startsWith(NexoraRoutes.INFO)))
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (tab == TopLevelTab.EXPLORE) exploreInitialCategory = null
                                    navigateTopLevel(tab.route)
                                },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.route}")
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NexoraNavHost(
                        navController = navController,
                        viewModel = viewModel,
                        exploreInitialCategory = exploreInitialCategory,
                        onSetExploreCategory = { exploreInitialCategory = it },
                        onLaunchPrompt = ::launchPromptInAssistant,
                        onNavigateTopLevel = ::navigateTopLevel,
                        onOpenLanguagePicker = { showLanguageDialog = true },
                        onOpenAccount = { showAccountDialog = true },
                        onSignOut = onSignOut
                    )
                }
            }
        }
    }

    if (showLanguageDialog) {
        LanguagePickerDialog(
            selectedLanguage = preferences.language,
            onSelect = { viewModel.updateLanguage(it) },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showAccountDialog) {
        AccountStatusDialog(
            isAuthenticated = viewModel.isAuthenticated,
            displayName = viewModel.displayUserName,
            email = viewModel.displayUserEmail,
            onSignOut = onSignOut,
            onDismiss = { showAccountDialog = false }
        )
    }
}

@Composable
private fun NexoraNavHost(
    navController: NavHostController,
    viewModel: NexoraViewModel,
    exploreInitialCategory: String?,
    onSetExploreCategory: (String?) -> Unit,
    onLaunchPrompt: (String, NexoraMode) -> Unit,
    onNavigateTopLevel: (String) -> Unit,
    onOpenLanguagePicker: () -> Unit,
    onOpenAccount: () -> Unit,
    onSignOut: () -> Unit
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val activeMode by viewModel.activeMode.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val streamingStatus by viewModel.streamingStatus.collectAsStateWithLifecycle()
    val streamingModelNote by viewModel.streamingModelNote.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val isFullScreenChat by viewModel.isFullScreenChat.collectAsStateWithLifecycle()
    val exhibitionState by viewModel.exhibitionState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = NexoraRoutes.HOME
    ) {
        composable(NexoraRoutes.HOME) {
            HomeScreen(
                preferences = preferences,
                isAuthenticated = viewModel.isAuthenticated,
                onAskPrompt = onLaunchPrompt,
                onNavigateExplore = { catId ->
                    onSetExploreCategory(catId)
                    onNavigateTopLevel(NexoraRoutes.EXPLORE)
                },
                onNavigateFeatures = {
                    navController.navigate("${NexoraRoutes.INFO}/${InfoSection.FEATURES.name}")
                },
                onNavigateExhibition = {
                    navController.navigate(NexoraRoutes.EXHIBITION)
                },
                onNavigateAbout = {
                    navController.navigate("${NexoraRoutes.INFO}/${InfoSection.ABOUT.name}")
                },
                onNavigatePrivacy = {
                    navController.navigate("${NexoraRoutes.INFO}/${InfoSection.PRIVACY.name}")
                },
                onNavigateTerms = {
                    navController.navigate("${NexoraRoutes.INFO}/${InfoSection.TERMS.name}")
                },
                onNavigateContact = {
                    navController.navigate("${NexoraRoutes.INFO}/${InfoSection.CONTACT.name}")
                },
                onOpenAccount = onOpenAccount
            )
        }

        composable(NexoraRoutes.ASSISTANT) {
            ChatScreen(
                conversations = conversations,
                activeConversationId = activeConversationId,
                messages = messages,
                activeMode = activeMode,
                preferences = preferences,
                isStreaming = isStreaming,
                chatError = chatError,
                isAuthenticated = viewModel.isAuthenticated,
                isFullScreen = isFullScreenChat,
                onToggleFullScreen = { viewModel.setFullScreenChat(it) },
                onSelectMode = { viewModel.selectMode(it) },
                onOpenLanguagePicker = onOpenLanguagePicker,
                onOpenExhibition = { navController.navigate(NexoraRoutes.EXHIBITION) },
                onOpenAccount = onOpenAccount,
                onNewChat = { viewModel.startNewChat() },
                onSelectConversation = { viewModel.selectConversation(it) },
                onRenameConversation = { id, title -> viewModel.renameConversation(id, title) },
                onDeleteConversation = { viewModel.deleteConversation(it) },
                onClearConversation = { viewModel.clearActiveConversation() },
                onSendMessage = { prompt, bmp -> viewModel.sendMessage(prompt, bmp) },
                onStopGeneration = { viewModel.stopGeneration() },
                onRegenerateResponse = { viewModel.regenerateLastResponse() },
                onRetryMessage = { viewModel.retryFailedMessage(it) },
                onDismissError = { viewModel.dismissError() },
                streamingStatus = streamingStatus,
                streamingModelNote = streamingModelNote
            )
        }

        composable(NexoraRoutes.EXPLORE) {
            ExploreItScreen(
                initialCategoryId = exploreInitialCategory,
                onLaunchTopicPrompt = onLaunchPrompt
            )
        }

        composable(NexoraRoutes.MORE) {
            MoreHubScreen(
                onOpenSettings = { navController.navigate(NexoraRoutes.SETTINGS) },
                onOpenExhibition = { navController.navigate(NexoraRoutes.EXHIBITION) },
                onOpenSection = { section ->
                    navController.navigate("${NexoraRoutes.INFO}/${section.name}")
                }
            )
        }

        composable(NexoraRoutes.SETTINGS) {
            SettingsScreen(
                preferences = preferences,
                isAuthenticated = viewModel.isAuthenticated,
                userDisplayName = viewModel.displayUserName,
                userEmail = viewModel.displayUserEmail,
                statusBanner = statusBanner,
                onUpdateLanguage = { viewModel.updateLanguage(it) },
                onUpdateThemeMode = { viewModel.updateThemeMode(it) },
                onUpdateResponseStyle = { viewModel.updateResponseStyle(it) },
                onUpdateSimpleExplanations = { viewModel.updateSimpleExplanations(it) },
                onUpdateDefaultMode = { viewModel.updateDefaultMode(it) },
                onUpdateModelTier = { viewModel.updateModelTier(it) },
                onClearAllHistory = { viewModel.clearAllHistory() },
                onSignOut = onSignOut,
                onDismissStatusBanner = { viewModel.dismissStatusBanner() }
            )
        }

        composable(NexoraRoutes.EXHIBITION) {
            ExhibitionScreen(
                state = exhibitionState,
                language = preferences.language,
                onRunPrompt = { prompt, mode -> viewModel.runExhibitionPrompt(prompt, mode) },
                onStopGeneration = { viewModel.stopExhibitionGeneration() },
                onOpenLanguagePicker = onOpenLanguagePicker,
                onExitExhibition = { navController.popBackStack() },
                onRetryPrompt = { viewModel.retryExhibitionPrompt() }
            )
        }

        composable(
            route = "${NexoraRoutes.INFO}/{section}",
            arguments = listOf(navArgument("section") { type = NavType.StringType })
        ) { entry ->
            val raw = entry.arguments?.getString("section")
            val section = InfoSection.entries.firstOrNull { it.name == raw } ?: InfoSection.ABOUT
            InfoDetailScreen(
                section = section,
                onBack = { navController.popBackStack() },
                onLaunchChatMode = { mode ->
                    viewModel.startNewChat(mode)
                    onNavigateTopLevel(NexoraRoutes.ASSISTANT)
                }
            )
        }
    }
}
