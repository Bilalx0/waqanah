package com.waqanah.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.waqanah.app.service.enforcement.BuiltInLists
import com.waqanah.app.service.enforcement.EnforcementMode
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

    val enforcementEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENFORCEMENT_ENABLED] ?: false
    }

    // Alias for backwards compatibility
    val isHardLockEnabledFlow: Flow<Boolean> = enforcementEnabledFlow

    val enforcementModeFlow: Flow<EnforcementMode> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[KEY_ENFORCEMENT_MODE] ?: EnforcementMode.HIDE_AND_SUSPEND.name
        try {
            EnforcementMode.valueOf(modeStr)
        } catch (_: Exception) {
            EnforcementMode.HIDE_AND_SUSPEND
        }
    }

    val allowedBrowserPackageFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ALLOWED_BROWSER_PACKAGE]
    }

    val allowListPackagesFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_ALLOW_LIST_PACKAGES] ?: emptySet()
    }

    val customBlocklistFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_BLOCKLIST] ?: emptySet()
    }

    val hideSystemBrowsersFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HIDE_SYSTEM_BROWSERS] ?: false
    }

    val hiddenByWaqanahFlow: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        val set = prefs[KEY_HIDDEN_BY_WAQANAH] ?: emptySet()
        set.mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size >= 2) {
                parts[0] to parts[1]
            } else null
        }.toMap()
    }

    val uninstalledByWaqanahFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_UNINSTALLED_BY_WAQANAH] ?: emptySet()
    }

    val lastEnforcementAtFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_ENFORCEMENT_AT] ?: 0L
    }

    val blockedPackagesFlow: Flow<Set<String>> = hiddenByWaqanahFlow.map { it.keys }

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

    suspend fun setEnforcementEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENFORCEMENT_ENABLED] = enabled
        }
    }

    suspend fun setHardLockState(enabled: Boolean) = setEnforcementEnabled(enabled)

    suspend fun setEnforcementMode(mode: EnforcementMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENFORCEMENT_MODE] = mode.name
        }
    }

    suspend fun setAllowedBrowserPackage(packageName: String?) {
        context.dataStore.edit { prefs ->
            if (packageName == null) {
                prefs.remove(KEY_ALLOWED_BROWSER_PACKAGE)
            } else {
                prefs[KEY_ALLOWED_BROWSER_PACKAGE] = packageName
            }
        }
    }

    suspend fun updateAllowList(packages: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ALLOW_LIST_PACKAGES] = packages
        }
    }

    suspend fun updateCustomBlocklist(packages: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_BLOCKLIST] = packages
        }
    }

    suspend fun setHideSystemBrowsers(hide: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HIDE_SYSTEM_BROWSERS] = hide
        }
    }

    suspend fun setHiddenByWaqanah(hiddenMap: Map<String, String>) {
        context.dataStore.edit { prefs ->
            val set = hiddenMap.map { "${it.key}|${it.value}" }.toSet()
            prefs[KEY_HIDDEN_BY_WAQANAH] = set
        }
    }

    suspend fun addHiddenPackages(newEntries: Map<String, String>) {
        context.dataStore.edit { prefs ->
            val currentSet = prefs[KEY_HIDDEN_BY_WAQANAH] ?: emptySet()
            val updatedSet = currentSet.toMutableSet()
            newEntries.forEach { (pkg, reason) ->
                updatedSet.add("$pkg|$reason")
            }
            prefs[KEY_HIDDEN_BY_WAQANAH] = updatedSet
        }
    }

    suspend fun removeHiddenPackages(packagesToRemove: Set<String>) {
        context.dataStore.edit { prefs ->
            val currentSet = prefs[KEY_HIDDEN_BY_WAQANAH] ?: emptySet()
            val updatedSet = currentSet.filterNot { entry ->
                val pkg = entry.substringBefore("|")
                packagesToRemove.contains(pkg)
            }.toSet()
            prefs[KEY_HIDDEN_BY_WAQANAH] = updatedSet
        }
    }

    suspend fun addUninstalledPackages(packages: Set<String>) {
        context.dataStore.edit { prefs ->
            val currentSet = prefs[KEY_UNINSTALLED_BY_WAQANAH] ?: emptySet()
            prefs[KEY_UNINSTALLED_BY_WAQANAH] = currentSet + packages
        }
    }

    suspend fun setLastEnforcementAt(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_ENFORCEMENT_AT] = timestamp
        }
    }

    companion object {
        private val KEY_SELECTED_TRANSLATION = stringPreferencesKey("selected_translation")
        private val KEY_SELECTED_RECITER = stringPreferencesKey("selected_reciter")
        private val KEY_ENFORCEMENT_ENABLED = booleanPreferencesKey("enforcement_enabled")
        private val KEY_ENFORCEMENT_MODE = stringPreferencesKey("enforcement_mode")
        private val KEY_ALLOWED_BROWSER_PACKAGE = stringPreferencesKey("allowed_browser_package")
        private val KEY_ALLOW_LIST_PACKAGES = stringSetPreferencesKey("allow_list_packages")
        private val KEY_CUSTOM_BLOCKLIST = stringSetPreferencesKey("custom_blocklist")
        private val KEY_HIDE_SYSTEM_BROWSERS = booleanPreferencesKey("hide_system_browsers")
        private val KEY_HIDDEN_BY_WAQANAH = stringSetPreferencesKey("hidden_by_waqanah")
        private val KEY_UNINSTALLED_BY_WAQANAH = stringSetPreferencesKey("uninstalled_by_waqanah")
        private val KEY_LAST_ENFORCEMENT_AT = longPreferencesKey("last_enforcement_at")

        val DEFAULT_BLOCKED_PACKAGES = BuiltInLists.blocklist
    }
}
