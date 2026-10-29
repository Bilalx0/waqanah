package com.waqanah.app.data.network

import com.waqanah.app.data.network.model.AyahEditionsResponseDto
import com.waqanah.app.data.network.model.EditionResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface QuranApiService {

    @GET("v1/edition")
    suspend fun getTranslations(
        @Query("format") format: String = "text",
        @Query("type") type: String = "translation"
    ): EditionResponseDto

    @GET("v1/edition")
    suspend fun getReciters(
        @Query("format") format: String = "audio",
        @Query("type") type: String = "versebyverse"
    ): EditionResponseDto

    @GET("v1/ayah/{ayahReference}/editions/quran-uthmani,{translationId},{reciterId}")
    suspend fun getAyahEditions(
        @Path("ayahReference") ayahReference: String,
        @Path("translationId") translationId: String,
        @Path("reciterId") reciterId: String
    ): AyahEditionsResponseDto

    companion object {
        const val BASE_URL = "https://api.alquran.cloud/"
    }
}
