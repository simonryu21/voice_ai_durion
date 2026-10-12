package com.lgcns.codeengine.data.repository.location

interface LocationRepository {
    suspend fun getCurrentLocation(): CurrentLocationResult
    fun hasAvailableProvider(): Boolean
}