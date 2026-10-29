package com.waqanah.app.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.waqanah.app.service.worker.QuranWidgetWorker

object WidgetKeys {
    val SURAH_NAME = stringPreferencesKey("surah_name")
    val AYAH_REF = stringPreferencesKey("ayah_ref")
    val ARABIC_TEXT = stringPreferencesKey("arabic_text")
    val TRANSLATION_TEXT = stringPreferencesKey("translation_text")
    val AUDIO_URL = stringPreferencesKey("audio_url")
}

class WaqanahGlanceWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                val surahName = prefs[WidgetKeys.SURAH_NAME] ?: "سُورَةُ البَقَرَةِ"
                val ayahRef = prefs[WidgetKeys.AYAH_REF] ?: "2:255"
                val arabicText = prefs[WidgetKeys.ARABIC_TEXT]
                    ?: "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ"
                val translationText = prefs[WidgetKeys.TRANSLATION_TEXT]
                    ?: "Allah - there is no deity except Him, the Ever-Living, the Sustainer of existence."

                WidgetContent(
                    surahName = surahName,
                    ayahRef = ayahRef,
                    arabicText = arabicText,
                    translationText = translationText
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        surahName: String,
        ayahRef: String,
        arabicText: String,
        translationText: String
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp)
                .background(ColorProvider(day = androidx.compose.ui.graphics.Color(0xFF1E293B), night = androidx.compose.ui.graphics.Color(0xFF0F172A))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$surahName ($ayahRef)",
                    style = TextStyle(
                        color = ColorProvider(day = androidx.compose.ui.graphics.Color(0xFF38BDF8), night = androidx.compose.ui.graphics.Color(0xFF38BDF8)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            Text(
                text = arabicText,
                style = TextStyle(
                    color = ColorProvider(day = androidx.compose.ui.graphics.Color.White, night = androidx.compose.ui.graphics.Color.White),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                ),
                modifier = GlanceModifier.fillMaxWidth()
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            Text(
                text = translationText,
                style = TextStyle(
                    color = ColorProvider(day = androidx.compose.ui.graphics.Color(0xFF94A3B8), night = androidx.compose.ui.graphics.Color(0xFF94A3B8)),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Start
                ),
                modifier = GlanceModifier.fillMaxWidth()
            )

            Spacer(modifier = GlanceModifier.height(12.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .background(ColorProvider(day = androidx.compose.ui.graphics.Color(0xFF0284C7), night = androidx.compose.ui.graphics.Color(0xFF0284C7)))
                        .clickable(actionRunCallback<RefreshWidgetActionCallback>())
                ) {
                    Text(
                        text = "Refresh Ayah 🔄",
                        style = TextStyle(
                            color = ColorProvider(day = androidx.compose.ui.graphics.Color.White, night = androidx.compose.ui.graphics.Color.White),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.padding(6.dp)
                    )
                }
            }
        }
    }
}

class RefreshWidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val workRequest = OneTimeWorkRequestBuilder<QuranWidgetWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
