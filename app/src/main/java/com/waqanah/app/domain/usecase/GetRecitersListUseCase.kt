package com.waqanah.app.domain.usecase

import com.waqanah.app.domain.model.Edition
import com.waqanah.app.domain.repository.QuranRepository
import kotlinx.coroutines.flow.Flow

class GetRecitersListUseCase(
    private val repository: QuranRepository
) {
    operator fun invoke(): Flow<Result<List<Edition>>> {
        return repository.getReciters()
    }
}
