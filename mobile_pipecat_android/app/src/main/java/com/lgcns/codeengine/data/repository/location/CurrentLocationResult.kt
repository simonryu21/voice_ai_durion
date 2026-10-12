package com.lgcns.codeengine.data.repository.location

data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
)

sealed class CurrentLocationResult {
    data class Success(val location: GeoLocation) : CurrentLocationResult()
    object LocationDisabled : CurrentLocationResult()
    object NoPermission : CurrentLocationResult()
    object Timeout : CurrentLocationResult()
}