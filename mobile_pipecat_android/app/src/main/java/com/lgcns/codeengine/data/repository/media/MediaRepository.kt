package com.lgcns.codeengine.data.repository.media

import java.io.File

interface MediaRepository {

    suspend fun saveToPublicMedia(
        source: ByteArray, relativePath: String = "", filename: String,
    ): String?

    suspend fun saveToAppCache(
        source: ByteArray, relativePath: String = "", filename: String,
    ): File?
}