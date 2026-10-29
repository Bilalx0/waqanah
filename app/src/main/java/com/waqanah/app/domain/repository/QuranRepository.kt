package com.waqanah.app.domain.repository

import com.waqanah.app.domain.model.AyahDetail
import com.waqanah.app.domain.model.Edition
import kotlinx.coroutines.flow.Flow

interface QuranRepository {
    fun getTranslations(): Flow<Result<List<Edition>>>
    fun getReciters(): Flow<Result<List<Edition>>>
    fun getAyahDetail(
        surahNumber: Int,
        ayahNumber: Int,
        translationId: String,
        reciterId: String
    ): Flow<Result<AyahDetail>>
}
