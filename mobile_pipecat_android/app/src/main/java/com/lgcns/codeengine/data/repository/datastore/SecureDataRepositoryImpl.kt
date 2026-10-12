package com.lgcns.codeengine.data.repository.datastore

import com.lgcns.codeengine.data.exception.SecureDataException
import javax.inject.Inject

class SecureDataRepositoryImpl @Inject constructor() : SecureDataStoreRepository {

    @Throws(SecureDataException::class)
    override suspend fun encryptSplit(
        key: String, plainText: String, customerNumber: String,
    ): String {
        val manager = SecureDataManager(key, customerNumber)
        val cipherText = manager.encrypt(plainText)
        val (localCipherText, remoteCipherText) = cipherText.splitHalf()
        manager.setString(localCipherText)
        return remoteCipherText
    }

    @Throws(SecureDataException::class)
    override suspend fun decryptJoin(
        key: String, remoteCipherText: String, customerNumber: String,
    ): String? {
        val manager = SecureDataManager(key, customerNumber)
        val localCipherText = manager.getString() ?: return null
        val cipherText = localCipherText + remoteCipherText
        return manager.decrypt(cipherText)
    }

    override suspend fun remove(keys: List<String>, customerNumber: String): Map<String, Boolean> {
        val result = mutableMapOf<String, Boolean>()
        keys.forEach { key ->
            val secure = SecureDataManager(key, customerNumber)
            val deleted = secure.remove()
            result[key] = deleted
        }
        return result
    }

    override suspend fun contains(keys: List<String>, customerNumber: String): Map<String, Boolean> {
        val result = mutableMapOf<String, Boolean>()
        keys.forEach { key ->
            val secure = SecureDataManager(key, customerNumber)
            val data = secure.getString()
            result[key] = !data.isNullOrEmpty()
        }
        return result
    }

    private fun String.splitHalf(): Pair<String, String> {
        val halfIndex = length / 2
        return substring(0, halfIndex) to substring(halfIndex, length)
    }

    override suspend fun setEncryptedValue(key: String, value: String) {
        val manager = SecureDataManager(key)
        manager.setString(value)
    }

    override suspend fun getDecryptedValue(key: String): String? {
        val manager = SecureDataManager(key)
        return manager.getString()
    }
}
