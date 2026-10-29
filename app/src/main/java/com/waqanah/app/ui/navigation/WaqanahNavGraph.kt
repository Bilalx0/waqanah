package com.waqanah.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.waqanah.app.ui.admin.FocusLockScreen
import com.waqanah.app.ui.quran.QuranReflectionScreen
import com.waqanah.app.ui.settings.SettingsScreen

@Composable
fun WaqanahNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavScreen.QuranReflection.route,
        modifier = modifier
    ) {
        composable(NavScreen.QuranReflection.route) {
            QuranReflectionScreen()
        }
        composable(NavScreen.FocusLock.route) {
            FocusLockScreen()
        }
        composable(NavScreen.Settings.route) {
            SettingsScreen()
        }
    }
}
