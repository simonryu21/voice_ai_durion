package com.lgcns.codeengine.data.repository.datastore

import com.lgcns.codeengine.data.exception.SecureDataException

interface SecureDataStoreRepository {

    @Throws(SecureDataException::class)
    suspend fun encryptSplit(key: String, plainText: String, customerNumber: String = ""): String

    @Throws(SecureDataException::class)
    suspend fun decryptJoin(key: String, remoteCipherText: String, customerNumber: String = ""): String?

    suspend fun remove(keys: List<String>, customerNumber: String = ""): Map<String, Boolean>

    suspend fun contains(keys: List<String>, customerNumber: String = ""): Map<String, Boolean>

    suspend fun setEncryptedValue(key: String, value: String)

    suspend fun getDecryptedValue(key: String): String?
}