package com.waqanah.app.data.network.model

import com.google.gson.annotations.SerializedName

data class AyahEditionsResponseDto(
    @SerializedName("code") val code: Int,
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: List<AyahEditionDataDto>
)

data class AyahEditionDataDto(
    @SerializedName("number") val number: Int,
    @SerializedName("text") val text: String,
    @SerializedName("audio") val audio: String?,
    @SerializedName("numberInSurah") val numberInSurah: Int,
    @SerializedName("edition") val edition: EditionDto,
    @SerializedName("surah") val surah: SurahDto
)

data class SurahDto(
    @SerializedName("number") val number: Int,
    @SerializedName("name") val name: String,
    @SerializedName("englishName") val englishName: String,
    @SerializedName("englishNameTranslation") val englishNameTranslation: String,
    @SerializedName("numberOfAyahs") val numberOfAyahs: Int,
    @SerializedName("revelationType") val revelationType: String
)
