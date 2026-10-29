package com.waqanah.app.data.repository

import com.waqanah.app.data.network.QuranApiService
import com.waqanah.app.domain.model.AyahDetail
import com.waqanah.app.domain.model.Edition
import com.waqanah.app.domain.repository.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class QuranRepositoryImpl(
    private val apiService: QuranApiService
) : QuranRepository {

    override fun getTranslations(): Flow<Result<List<Edition>>> = flow {
        try {
            val response = apiService.getTranslations()
            val editions = response.data.map { dto ->
                Edition(
                    identifier = dto.identifier,
                    language = dto.language,
                    name = dto.name,
                    englishName = dto.englishName,
                    format = dto.format,
                    type = dto.type
                )
            }
            emit(Result.success(editions))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getReciters(): Flow<Result<List<Edition>>> = flow {
        try {
            val response = apiService.getReciters()
            val reciters = response.data.map { dto ->
                Edition(
                    identifier = dto.identifier,
                    language = dto.language,
                    name = dto.name,
                    englishName = dto.englishName,
                    format = dto.format,
                    type = dto.type
                )
            }
            emit(Result.success(reciters))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun getAyahDetail(
        surahNumber: Int,
        ayahNumber: Int,
        translationId: String,
        reciterId: String
    ): Flow<Result<AyahDetail>> = flow {
        try {
            val ayahRef = "$surahNumber:$ayahNumber"
            val response = apiService.getAyahEditions(
                ayahReference = ayahRef,
                translationId = translationId,
                reciterId = reciterId
            )

            val editionsData = response.data
            val uthmaniEdition = editionsData.find { it.edition.identifier == "quran-uthmani" }
                ?: editionsData.firstOrNull()
            val translationEdition = editionsData.find { it.edition.identifier == translationId }
                ?: editionsData.getOrNull(1)
            val audioEdition = editionsData.find { it.edition.identifier == reciterId }
                ?: editionsData.getOrNull(2)

            if (uthmaniEdition != null && translationEdition != null) {
                val detail = AyahDetail(
                    number = uthmaniEdition.number,
                    numberInSurah = uthmaniEdition.numberInSurah,
                    surahNumber = uthmaniEdition.surah.number,
                    surahNameArabic = uthmaniEdition.surah.name,
                    surahNameEnglish = uthmaniEdition.surah.englishName,
                    arabicText = uthmaniEdition.text,
                    translationText = translationEdition.text,
                    translationLanguage = translationEdition.edition.language,
                    audioUrl = audioEdition?.audio
                )
                emit(Result.success(detail))
            } else {
                emit(Result.failure(IllegalStateException("Ayah edition data is incomplete.")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)
}
