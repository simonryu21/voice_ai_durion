package com.lgcns.codeengine.data.repository.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 *  WebView에서 사용하는 DataStore입니다. SharedPreference 대신 DataStore를 사용합니다.
 *  Reference: https://developer.android.com/codelabs/android-preferences-datastore
 */
class WebDataStoreRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : WebDataStoreRepository {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "CODEENGINE_WEB_DATASTORE")
    private val datastoreManager = DataStoreManager(context.dataStore)

    override suspend fun getDataStoreString(key: String): String? {
        return datastoreManager.getDataStoreStringOnce(key)
    }

    override fun getDataStoreStringFlow(key: String): Flow<String?> {
        return datastoreManager.getDataStoreString(key)
    }

    override suspend fun setDataStoreString(key: String, value: String) {
        datastoreManager.setDataStoreString(key, value)
    }

    override suspend fun removeDataStoreString(key: String) {
        datastoreManager.removeDataStoreString(key)
    }
}
