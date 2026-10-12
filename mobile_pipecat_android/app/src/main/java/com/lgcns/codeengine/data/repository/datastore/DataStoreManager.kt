package com.lgcns.codeengine.data.repository.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException

/**
 *  datastore 에서 key 에 해당하는 value 를
 *  1. Flow 로 반환 - getDataStore<TYPE>
 *  2. value 값 하나만 반환 - getDataStore<TYPE>Once
 *  3. 저장 - setDataStore<TYPE>
 */
class DataStoreManager(private val dataStore: DataStore<Preferences>) {

    suspend fun setDataStoreInt(key: String, value: Int) {
        setDataStore(intPreferencesKey(key), value)
    }

    suspend fun setDataStoreBoolean(key: String, value: Boolean) {
        setDataStore(booleanPreferencesKey(key), value)
    }

    suspend fun setDataStoreString(key: String, value: String) {
        setDataStore(stringPreferencesKey(key), value)
    }

    private suspend fun <T> setDataStore(key: Preferences.Key<T>, value: T) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    fun getDataStoreInt(key: String): Flow<Int?> {
        return getDataStore(intPreferencesKey(key))
    }

    fun getDataStoreBoolean(key: String): Flow<Boolean?> {
        return getDataStore(booleanPreferencesKey(key))

    }

    fun getDataStoreString(key: String): Flow<String?> {
        return getDataStore(stringPreferencesKey(key))
    }

    suspend fun getDataStoreIntOnce(key: String): Int? {
        return try {
            getDataStoreInt(key).first()
        } catch (e: Exception) {
            Timber.e(e)
            null
        }
    }

    suspend fun getDataStoreBooleanOnce(key: String): Boolean? {
        return try {
            getDataStoreBoolean(key).first()
        } catch (e: Exception) {
            Timber.e(e)
            null
        }

    }

    suspend fun getDataStoreStringOnce(key: String): String? {
        return try {
            getDataStoreString(key).first()
        } catch (e: Exception) {
            Timber.e(e)
            null
        }
    }

    private fun <T> getDataStore(key: Preferences.Key<T>): Flow<T?> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[key]
            }
    }

    private suspend fun <T> removeDataStore(key: Preferences.Key<T>) {
        dataStore.edit {
            if (it.contains(key)) {
                it.remove(key)
            }
        }
    }

    suspend fun removeDataStoreString(key: String) {
        removeDataStore(stringPreferencesKey(key))
    }

    suspend fun clearDataStore() {
        dataStore.edit {
            it.clear()
        }
    }
}
