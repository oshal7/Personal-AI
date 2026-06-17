package com.personalai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.personalai.app.domain.model.ModelRegistry
import com.personalai.app.service.ModelDownloadService
import com.personalai.app.ui.chat.ChatScreen
import com.personalai.app.ui.chat.ChatViewModel
import com.personalai.app.ui.modeldownload.ModelDownloadScreen
import com.personalai.app.ui.modeldownload.ModelDownloadViewModel
import com.personalai.app.ui.theme.PersonalAITheme

private const val ROUTE_DOWNLOAD = "download"
private const val ROUTE_CHAT = "chat"

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
    val startDestination = if (app.modelRepository.isDownloaded(ModelRegistry.default)) ROUTE_CHAT else ROUTE_DOWNLOAD

    NavHost(navController = navController, startDestination = startDestination) {
        composable(ROUTE_DOWNLOAD) {
            val viewModel: ModelDownloadViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        ModelDownloadViewModel(app.modelRepository) { ModelDownloadService.start(app.applicationContext) }
                    }
                }
            )
            ModelDownloadScreen(viewModel) {
                navController.navigate(ROUTE_CHAT) {
                    popUpTo(ROUTE_DOWNLOAD) { inclusive = true }
                }
            }
        }
        composable(ROUTE_CHAT) {
            val viewModel: ChatViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ChatViewModel(app.chatRepository, app.modelRepository, app.llamaSession) }
                }
            )
            ChatScreen(viewModel)
        }
    }
}
