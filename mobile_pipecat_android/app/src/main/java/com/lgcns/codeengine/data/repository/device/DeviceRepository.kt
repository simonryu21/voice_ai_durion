package com.lgcns.codeengine.data.repository.device

interface DeviceRepository {
    suspend fun getDeviceId(): String
    fun getAppVersion(): String
    suspend fun getVersionCode(): String
    fun getAppName(): String
    fun getAppPackageName(): String

    fun areNotificationsEnabled(): Boolean

    fun setClipboardText(label: String, text: String)
    fun getClipboardText(): String

    fun getPhoneNumber(): String
    fun getSimOperator(): String

    suspend fun getPushTokenOrNull(): String?
}