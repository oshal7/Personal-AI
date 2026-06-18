package com.personalai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.personalai.app.service.ModelDownloadService
import com.personalai.app.ui.chat.ChatScreen
import com.personalai.app.ui.chat.ChatViewModel
import com.personalai.app.ui.history.HistoryDrawerContent
import com.personalai.app.ui.history.HistoryViewModel
import com.personalai.app.ui.modeldownload.ModelDownloadScreen
import com.personalai.app.ui.modeldownload.ModelDownloadViewModel
import com.personalai.app.ui.settings.SettingsScreen
import com.personalai.app.ui.settings.SettingsViewModel
import com.personalai.app.ui.tasks.TasksScreen
import com.personalai.app.ui.tasks.TasksViewModel
import com.personalai.app.ui.theme.PersonalAITheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val ROUTE_DOWNLOAD = "download"
private const val ROUTE_CHAT_PATTERN = "chat/{sessionId}"
private const val ROUTE_TASKS = "tasks"
private const val ROUTE_SETTINGS = "settings"
private const val ARG_SESSION_ID = "sessionId"

private fun chatRoute(sessionId: Long) = "chat/$sessionId"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PersonalAiApplication

        setContent {
            PersonalAITheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PersonalAiNavHost(app)
                }
            }
        }
    }
}

@Composable
private fun PersonalAiNavHost(app: PersonalAiApplication) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val activeModel = app.userPreferences.activeModel.first()
        startDestination = if (app.modelRepository.isDownloaded(activeModel)) chatRoute(0L) else ROUTE_DOWNLOAD
    }

    val destination = startDestination ?: return

    NavHost(navController = navController, startDestination = destination) {
        composable(ROUTE_DOWNLOAD) {
            val viewModel: ModelDownloadViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        ModelDownloadViewModel(app.modelRepository) { ModelDownloadService.start(app.applicationContext) }
                    }
                }
            )
            ModelDownloadScreen(viewModel) {
                navController.navigate(chatRoute(0L)) {
                    popUpTo(ROUTE_DOWNLOAD) { inclusive = true }
                }
            }
        }
        composable(
            route = ROUTE_CHAT_PATTERN,
            arguments = listOf(navArgument(ARG_SESSION_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong(ARG_SESSION_ID) ?: 0L
            ChatSessionRoute(app, navController, sessionId)
        }
        composable(ROUTE_TASKS) {
            val viewModel: TasksViewModel = viewModel(
                factory = viewModelFactory { initializer { TasksViewModel(app.taskRepository) } }
            )
            TasksScreen(viewModel, onBack = { navController.popBackStack() })
        }
        composable(ROUTE_SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(app.modelRepository, app.userPreferences, app.llamaSession) { model ->
                            ModelDownloadService.start(app.applicationContext, model)
                        }
                    }
                }
            )
            SettingsScreen(viewModel, onBack = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSessionRoute(app: PersonalAiApplication, navController: NavHostController, sessionId: Long) {
    val chatViewModel: ChatViewModel = viewModel(
        key = "chat-$sessionId",
        factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    chatRepository = app.chatRepository,
                    modelRepository = app.modelRepository,
                    llamaSession = app.llamaSession,
                    taskRepository = app.taskRepository,
                    speechInputManager = app.speechInputManager,
                    ttsManager = app.ttsManager,
                    userPreferences = app.userPreferences,
                    initialSessionId = sessionId,
                )
            }
        }
    )
    val historyViewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory { initializer { HistoryViewModel(app.chatRepository) } }
    )
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HistoryDrawerContent(
                viewModel = historyViewModel,
                activeSessionId = sessionId,
                onNewChat = {
                    scope.launch { drawerState.close() }
                    navController.navigate(chatRoute(0L)) {
                        popUpTo(ROUTE_CHAT_PATTERN) { inclusive = true }
                    }
                },
                onSelectSession = { id ->
                    scope.launch { drawerState.close() }
                    navController.navigate(chatRoute(id)) {
                        popUpTo(ROUTE_CHAT_PATTERN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    navController.navigate(ROUTE_SETTINGS)
                },
            )
        }
    ) {
        ChatScreen(
            viewModel = chatViewModel,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onNewChat = {
                navController.navigate(chatRoute(0L)) {
                    popUpTo(ROUTE_CHAT_PATTERN) { inclusive = true }
                }
            },
            onOpenTasks = { navController.navigate(ROUTE_TASKS) },
        )
    }
}
