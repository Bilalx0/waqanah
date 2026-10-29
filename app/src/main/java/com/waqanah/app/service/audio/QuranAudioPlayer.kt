package com.waqanah.app.service.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AudioPlayerState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null
)

class QuranAudioPlayer(context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressUpdateJob: Job? = null

    private val _playerState = MutableStateFlow(AudioPlayerState())
    val playerState: StateFlow<AudioPlayerState> = _playerState.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(
                    isPlaying = isPlaying,
                    isLoading = false
                )
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _playerState.value = _playerState.value.copy(isLoading = true, error = null)
                    }
                    Player.STATE_READY -> {
                        _playerState.value = _playerState.value.copy(
                            isLoading = false,
                            durationMs = player.duration.coerceAtLeast(0L),
                            error = null
                        )
                    }
                    Player.STATE_ENDED -> {
                        _playerState.value = _playerState.value.copy(
                            isPlaying = false,
                            isLoading = false,
                            currentPositionMs = 0L
                        )
                        stopProgressTracker()
                    }
                    Player.STATE_IDLE -> {
                        _playerState.value = _playerState.value.copy(
                            isPlaying = false,
                            isLoading = false
                        )
                        stopProgressTracker()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _playerState.value = _playerState.value.copy(
                    isPlaying = false,
                    isLoading = false,
                    error = error.localizedMessage ?: "Audio playback error"
                )
                stopProgressTracker()
            }
        })
    }

    fun playAyah(audioUrl: String) {
        if (audioUrl.isBlank()) return
        stopProgressTracker()
        val mediaItem = MediaItem.fromUri(audioUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        _playerState.value = _playerState.value.copy(isLoading = true, error = null)
    }

    fun pauseAudio() {
        player.pause()
    }

    fun resumeAudio() {
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
        }
        player.play()
    }

    fun stopAudio() {
        player.stop()
        stopProgressTracker()
        _playerState.value = AudioPlayerState()
    }

    fun releasePlayer() {
        stopProgressTracker()
        player.release()
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressUpdateJob = scope.launch {
            while (isActive) {
                _playerState.value = _playerState.value.copy(
                    currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = player.duration.coerceAtLeast(0L)
                )
                delay(500L)
            }
        }
    }

    private fun stopProgressTracker() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }
}
