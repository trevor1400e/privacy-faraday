package com.privacy.faraday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.privacy.faraday.ui.chat.ChatScreen
import com.privacy.faraday.ui.chat.ConversationListScreen
import com.privacy.faraday.ui.chat.NewConversationScreen
import com.privacy.faraday.ui.debug.DebugHomeScreen
import com.privacy.faraday.ui.settings.SettingsScreen
import com.privacy.faraday.ui.theme.FaradayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FaradayTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "conversations") {
                    composable("conversations") {
                        ConversationListScreen(
                            onNavigateToChat = { address ->
                                navController.navigate("chat/$address")
                            },
                            onNavigateToNewConversation = {
                                navController.navigate("new_conversation")
                            },
                            onNavigateToSettings = {
                                navController.navigate("settings")
                            }
                        )
                    }
                    composable("chat/{address}") {
                        ChatScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("new_conversation") {
                        NewConversationScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToChat = { address ->
                                navController.popBackStack()
                                navController.navigate("chat/$address")
                            }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToDebug = { navController.navigate("debug") }
                        )
                    }
                    composable("debug") {
                        DebugHomeScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
