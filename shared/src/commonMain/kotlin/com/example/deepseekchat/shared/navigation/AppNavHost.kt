package com.example.deepseekchat.shared.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.deepseekchat.shared.chat.ChatRoute
import com.example.deepseekchat.shared.settings.SettingsScreen

private const val RouteChat = "chat"
private const val RouteSettings = "settings"

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = RouteChat
    ) {
        composable(RouteChat) {
            ChatRoute(onOpenSettings = { navController.navigate(RouteSettings) })
        }

        composable(RouteSettings) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
