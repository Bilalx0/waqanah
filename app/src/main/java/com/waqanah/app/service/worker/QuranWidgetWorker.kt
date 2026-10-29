package com.waqanah.app.service.worker

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.waqanah.app.data.network.QuranApiService
import com.waqanah.app.data.repository.QuranRepositoryImpl
import com.waqanah.app.domain.usecase.GetDailyAyahUseCase
import com.waqanah.app.ui.widget.WidgetKeys
import com.waqanah.app.ui.widget.WaqanahGlanceWidget
import kotlinx.coroutines.flow.firstOrNull
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.random.Random

class QuranWidgetWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val retrofit = Retrofit.Builder()
                .baseUrl(QuranApiService.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val apiService = retrofit.create(QuranApiService::class.java)
            val repository = QuranRepositoryImpl(apiService)
            val getDailyAyahUseCase = GetDailyAyahUseCase(repository)

            val popularAyahs = listOf(
                Pair(1, 1), Pair(2, 255), Pair(2, 286), Pair(3, 190),
                Pair(24, 35), Pair(36, 58), Pair(55, 13), Pair(67, 1), Pair(112, 1)
            )
            val randomPair = popularAyahs[Random.nextInt(popularAyahs.size)]

            val ayahResult = getDailyAyahUseCase(
                surahNumber = randomPair.first,
                ayahNumber = randomPair.second,
                translationId = "en.sahih",
                reciterId = "ar.alafasy"
            ).firstOrNull()

            if (ayahResult != null && ayahResult.isSuccess) {
                val detail = ayahResult.getOrThrow()
                val glanceManager = GlanceAppWidgetManager(context)
                val glanceIds = glanceManager.getGlanceIds(WaqanahGlanceWidget::class.java)

                glanceIds.forEach { glanceId ->
                    updateAppWidgetState(context, glanceId) { prefs ->
                        prefs[WidgetKeys.SURAH_NAME] = detail.surahNameArabic
                        prefs[WidgetKeys.AYAH_REF] = "${detail.surahNumber}:${detail.numberInSurah}"
                        prefs[WidgetKeys.ARABIC_TEXT] = detail.arabicText
                        prefs[WidgetKeys.TRANSLATION_TEXT] = detail.translationText
                        detail.audioUrl?.let { url ->
                            prefs[WidgetKeys.AUDIO_URL] = url
                        }
                    }
                    WaqanahGlanceWidget().update(context, glanceId)
                }
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
