package com.waqanah.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.waqanah.app.BuildConfig
import com.waqanah.app.WaqanahApplication
import com.waqanah.app.data.local.WaqanahPreferences
import com.waqanah.app.data.network.QuranApiService
import com.waqanah.app.data.repository.QuranRepositoryImpl
import com.waqanah.app.domain.model.Edition
import com.waqanah.app.domain.usecase.GetRecitersListUseCase
import com.waqanah.app.domain.usecase.GetTranslationsListUseCase
import com.waqanah.app.service.admin.DeviceOwnerManager
import com.waqanah.app.service.admin.DeviceOwnerStatus
import com.waqanah.app.service.enforcement.ShieldServiceController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class SettingsUiState(
    val selectedTranslation: String = "en.sahih",
    val selectedReciter: String = "ar.alafasy",
    val isHardLockEnabled: Boolean = false,
    val blockedPackages: Set<String> = emptySet(),
    val deviceOwnerStatus: DeviceOwnerStatus = DeviceOwnerStatus.NotProvisioned
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WaqanahApplication
    private val preferences = WaqanahPreferences(application)
    val deviceOwnerManager: DeviceOwnerManager = app.deviceOwnerManager
    private val enforcementManager = app.enforcementManager

    private val retrofit = Retrofit.Builder()
        .baseUrl(QuranApiService.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(QuranApiService::class.java)
    private val repository = QuranRepositoryImpl(apiService)
    private val getTranslationsListUseCase = GetTranslationsListUseCase(repository)
    private val getRecitersListUseCase = GetRecitersListUseCase(repository)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.selectedTranslationFlow,
        preferences.selectedReciterFlow,
        preferences.isHardLockEnabledFlow,
        preferences.blockedPackagesFlow,
        deviceOwnerManager.status
    ) { translation, reciter, hardLock, blocked, ownerStatus ->
        SettingsUiState(
            selectedTranslation = translation,
            selectedReciter = reciter,
            isHardLockEnabled = hardLock,
            blockedPackages = blocked,
            deviceOwnerStatus = ownerStatus
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = SettingsUiState()
    )

    private val _translationsState = MutableStateFlow<List<Edition>>(emptyList())
    val translationsState: StateFlow<List<Edition>> = _translationsState

    private val _recitersState = MutableStateFlow<List<Edition>>(emptyList())
    val recitersState: StateFlow<List<Edition>> = _recitersState

    init {
        loadMetadata()
    }

    private fun loadMetadata() {
        viewModelScope.launch {
            getTranslationsListUseCase().collect { result ->
                result.onSuccess { list ->
                    _translationsState.value = list
                }
            }
        }
        viewModelScope.launch {
            getRecitersListUseCase().collect { result ->
                result.onSuccess { list ->
                    _recitersState.value = list
                }
            }
        }
    }

    fun updateTranslation(translationId: String) {
        viewModelScope.launch {
            preferences.updateTranslation(translationId)
        }
    }

    fun updateReciter(reciterId: String) {
        viewModelScope.launch {
            preferences.updateReciter(reciterId)
        }
    }

    fun removeDeviceOwnerDebug() {
        if (BuildConfig.DEBUG) {
            viewModelScope.launch {
                enforcementManager.disableAndRelease()
                ShieldServiceController.sync(app)
                deviceOwnerManager.clearDeviceOwnerApp()
            }
        }
    }

    fun refreshOwnerStatus() {
        deviceOwnerManager.refresh()
    }
}
