package com.waqanah.app.ui.quran

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.waqanah.app.data.local.WaqanahPreferences
import com.waqanah.app.data.network.QuranApiService
import com.waqanah.app.data.repository.QuranRepositoryImpl
import com.waqanah.app.domain.model.AyahDetail
import com.waqanah.app.domain.model.Edition
import com.waqanah.app.domain.usecase.GetDailyAyahUseCase
import com.waqanah.app.domain.usecase.GetRecitersListUseCase
import com.waqanah.app.domain.usecase.GetTranslationsListUseCase
import com.waqanah.app.service.audio.AudioPlayerState
import com.waqanah.app.service.audio.QuranAudioPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.random.Random

data class QuranUiState(
    val isLoading: Boolean = true,
    val ayahDetail: AyahDetail? = null,
    val translations: List<Edition> = emptyList(),
    val reciters: List<Edition> = emptyList(),
    val selectedTranslationId: String = "en.sahih",
    val selectedReciterId: String = "ar.alafasy",
    val errorMessage: String? = null
)

class QuranViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = WaqanahPreferences(application)

    private val retrofit = Retrofit.Builder()
        .baseUrl(QuranApiService.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(QuranApiService::class.java)
    private val repository = QuranRepositoryImpl(apiService)

    private val getDailyAyahUseCase = GetDailyAyahUseCase(repository)
    private val getTranslationsListUseCase = GetTranslationsListUseCase(repository)
    private val getRecitersListUseCase = GetRecitersListUseCase(repository)

    val audioPlayer = QuranAudioPlayer(application)
    val audioPlayerState: StateFlow<AudioPlayerState> = audioPlayer.playerState

    private val _uiState = MutableStateFlow(QuranUiState())
    val uiState: StateFlow<QuranUiState> = _uiState.asStateFlow()

    private var currentSurah: Int = 2
    private var currentAyah: Int = 255

    init {
        loadMetadataAndInitialAyah()
    }

    private fun loadMetadataAndInitialAyah() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val savedTranslation = preferences.selectedTranslationFlow.first()
            val savedReciter = preferences.selectedReciterFlow.first()

            _uiState.value = _uiState.value.copy(
                selectedTranslationId = savedTranslation,
                selectedReciterId = savedReciter
            )

            getTranslationsListUseCase().collect { result ->
                result.onSuccess { translations ->
                    _uiState.value = _uiState.value.copy(translations = translations)
                }
            }

            getRecitersListUseCase().collect { result ->
                result.onSuccess { reciters ->
                    _uiState.value = _uiState.value.copy(reciters = reciters)
                }
            }

            fetchAyah(currentSurah, currentAyah)
        }
    }

    fun fetchAyah(surah: Int, ayah: Int) {
        currentSurah = surah
        currentAyah = ayah
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val currentState = _uiState.value

            getDailyAyahUseCase(
                surahNumber = surah,
                ayahNumber = ayah,
                translationId = currentState.selectedTranslationId,
                reciterId = currentState.selectedReciterId
            ).collect { result ->
                result.onSuccess { detail ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        ayahDetail = detail,
                        errorMessage = null
                    )
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Failed to load Ayah reflection"
                    )
                }
            }
        }
    }

    fun loadNextRandomAyah() {
        audioPlayer.stopAudio()
        val popularSurahsAndAyahs = listOf(
            Pair(1, 1), Pair(2, 255), Pair(2, 286), Pair(3, 190),
            Pair(24, 35), Pair(36, 58), Pair(55, 13), Pair(67, 1), Pair(112, 1)
        )
        val nextPair = popularSurahsAndAyahs[Random.nextInt(popularSurahsAndAyahs.size)]
        fetchAyah(nextPair.first, nextPair.second)
    }

    fun selectTranslation(translationId: String) {
        _uiState.value = _uiState.value.copy(selectedTranslationId = translationId)
        viewModelScope.launch { preferences.updateTranslation(translationId) }
        fetchAyah(currentSurah, currentAyah)
    }

    fun selectReciter(reciterId: String) {
        _uiState.value = _uiState.value.copy(selectedReciterId = reciterId)
        viewModelScope.launch { preferences.updateReciter(reciterId) }
        fetchAyah(currentSurah, currentAyah)
    }

    fun toggleAudioPlayback() {
        val currentAudioState = audioPlayerState.value
        val audioUrl = _uiState.value.ayahDetail?.audioUrl

        if (currentAudioState.isPlaying) {
            audioPlayer.pauseAudio()
        } else {
            if (audioUrl != null) {
                audioPlayer.playAyah(audioUrl)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.releasePlayer()
    }
}
