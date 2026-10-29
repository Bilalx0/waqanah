package com.waqanah.app.domain.model

data class AyahDetail(
    val number: Int,
    val numberInSurah: Int,
    val surahNumber: Int,
    val surahNameArabic: String,
    val surahNameEnglish: String,
    val arabicText: String,
    val translationText: String,
    val translationLanguage: String,
    val audioUrl: String?
)
