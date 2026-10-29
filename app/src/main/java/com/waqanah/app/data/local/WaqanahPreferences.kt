package com.waqanah.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "waqanah_preferences")

class WaqanahPreferences(private val context: Context) {

    val selectedTranslationFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_TRANSLATION] ?: "en.sahih"
    }

    val selectedReciterFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_RECITER] ?: "ar.alafasy"
    }

    val isHardLockEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HARD_LOCK_ENABLED] ?: false
    }

    val blockedPackagesFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCKED_PACKAGES] ?: DEFAULT_BLOCKED_PACKAGES
    }

    suspend fun updateTranslation(translationId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SELECTED_TRANSLATION] = translationId
        }
    }

    suspend fun updateReciter(reciterId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SELECTED_RECITER] = reciterId
        }
    }

    suspend fun setHardLockState(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HARD_LOCK_ENABLED] = enabled
        }
    }

    suspend fun updateBlockedPackages(packages: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BLOCKED_PACKAGES] = packages
        }
    }

    companion object {
        private val KEY_SELECTED_TRANSLATION = stringPreferencesKey("selected_translation")
        private val KEY_SELECTED_RECITER = stringPreferencesKey("selected_reciter")
        private val KEY_HARD_LOCK_ENABLED = booleanPreferencesKey("hard_lock_enabled")
        private val KEY_BLOCKED_PACKAGES = stringSetPreferencesKey("blocked_packages")

        val DEFAULT_BLOCKED_PACKAGES = setOf(
            "com.UCMobile.intl",
            "com.nemo.vidmate",
            "com.expressvpn.vpn",
            "com.nordvpn.android",
            "com.surfshark.vpn",
            "org.torproject.torbrowser"
        )
    }
}
