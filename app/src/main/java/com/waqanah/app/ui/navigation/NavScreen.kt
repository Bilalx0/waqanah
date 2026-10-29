package com.waqanah.app.ui.navigation

sealed class NavScreen(val route: String, val title: String, val icon: String) {
    object QuranReflection : NavScreen("quran_reflection", "Reflection", "📖")
    object FocusLock : NavScreen("focus_lock", "Focus Admin", "🔒")
    object Settings : NavScreen("settings", "Settings", "⚙️")
}
