package com.lgcns.codeengine.data.repository.datastore

import kotlinx.coroutines.flow.Flow

/**
 *  WebView에서 사용하는 DataStore입니다. SharedPreference 대신 DataStore를 사용합니다.
 *  Reference: https://developer.android.com/codelabs/android-preferences-datastore
 */
interface WebDataStoreRepository {
    fun getDataStoreStringFlow(key: String): Flow<String?>
    suspend fun getDataStoreString(key: String): String?
    suspend fun setDataStoreString(key: String, value: String)
    suspend fun removeDataStoreString(key: String)
}
