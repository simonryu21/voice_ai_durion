package com.lgcns.codeengine.data.repository.datastore

import kotlinx.coroutines.flow.Flow

interface DataStoreRepository {
    fun getDataStoreInt(key: String): Flow<Int?>
    fun getDataStoreBoolean(key: String): Flow<Boolean?>
    fun getDataStoreString(key: String): Flow<String?>

    suspend fun getDataStoreIntOnce(key: String): Int?
    suspend fun getDataStoreBooleanOnce(key: String): Boolean?
    suspend fun getDataStoreStringOnce(key: String): String?

    suspend fun setDataStoreInt(key: String, value: Int)
    suspend fun setDataStoreBoolean(key: String, value: Boolean)
    suspend fun setDataStoreString(key: String, value: String)

    fun getDataStoreKey(plainText: String, key: String): String

    suspend fun removeDataStoreString(key: String)

    companion object {
        const val REACT_TEST_URL = "react_test_url"
        const val TOKEN_KEY = "jwt_token"
        const val AD_ID = "ad_id"
        const val SHOWN_PERMISSION_NOTICE = "shown_permission_notice"
    }
}