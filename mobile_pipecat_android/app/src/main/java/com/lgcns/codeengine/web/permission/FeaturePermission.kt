package com.lgcns.codeengine.web.permission

typealias Permission = String

interface FeaturePermission {
    suspend fun grant(): Boolean

    fun isGranted(): Boolean
}