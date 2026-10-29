package com.waqanah.app.domain.usecase

import com.waqanah.app.domain.model.AyahDetail
import com.waqanah.app.domain.repository.QuranRepository
import kotlinx.coroutines.flow.Flow

class GetDailyAyahUseCase(
    private val repository: QuranRepository
) {
    operator fun invoke(
        surahNumber: Int,
        ayahNumber: Int,
        translationId: String,
        reciterId: String
    ): Flow<Result<AyahDetail>> {
        return repository.getAyahDetail(surahNumber, ayahNumber, translationId, reciterId)
    }
}
