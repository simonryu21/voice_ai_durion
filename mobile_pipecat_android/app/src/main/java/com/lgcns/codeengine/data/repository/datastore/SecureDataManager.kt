package com.lgcns.codeengine.data.repository.datastore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.lgcns.codeengine.data.exception.SecureDataError
import com.lgcns.codeengine.data.exception.SecureDataException
import com.lgcns.codeengine.util.extension.toHexString
import com.lgcns.codeengine.util.extension.plusYears
import java.security.*
import java.security.cert.X509Certificate
import java.util.*
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException
import javax.security.auth.x500.X500Principal

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val CIPHER_ALGORITHM =
    "${KeyProperties.KEY_ALGORITHM_RSA}/${KeyProperties.BLOCK_MODE_ECB}/${KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1}"
private const val DEFAULT_VALIDITY_PERIOD_YEAR = 2

class SecureDataManager(
    dataKey: String,
    customerNumber: String = "",
) {

    private val cipherAlias: String by lazy { "$dataKey.${customerNumber.toByteArrayHexString()}" }
    private val cipher by lazy { SecureDataCipher(alias = cipherAlias) }

    private val dataAlias: String by lazy { "${cipherAlias}appendData" }
    private val dataStore by lazy { SecureDataStore(alias = dataAlias) }

    @Throws(SecureDataException::class)
    fun encrypt(plainText: String): String {
        try {
            return cipher.encrypt(plainText)
        } catch (e: Exception) {
            throw e.asSecureDataException()
        }
    }

    @Throws(SecureDataException::class)
    fun decrypt(cipherText: String): String {
        try {
            return cipher.decrypt(cipherText)
        } catch (e: Exception) {
            throw  e.asSecureDataException()
        }
    }

    @Throws(SecureDataException::class)
    fun setString(value: String) {
        try {
            dataStore.setString(value)
        } catch (e: Exception) {
            throw e.asSecureDataException()
        }
    }

    @Throws(SecureDataException::class)
    fun getString(): String? {
        return try {
            dataStore.getString()
        } catch (e: Exception) {
            throw e.asSecureDataException()
        }
    }

    fun remove(): Boolean {
        return dataStore.remove()
    }

    private fun Exception.asSecureDataException(): SecureDataException =
        when (this) {
            is NoSuchAlgorithmException,
            is NoSuchPaddingException ->
                SecureDataException(SecureDataError.UNSUPPORTED, cause = this)

            is InvalidKeyException -> SecureDataException(
                SecureDataError.INVALID_STATE,
                cause = this
            )

            is IllegalBlockSizeException,
            is BadPaddingException ->
                SecureDataException(SecureDataError.INVALID_DATA, cause = this)

            else -> SecureDataException(SecureDataError.UNKNOWN, cause = this)
        }
}


private class SecureDataCipher(
    private val alias: String,
) {

    private val keyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    private val keyPair by lazy {
        keyStore.getEntryKeyPair(alias) ?: generateKeyPair(alias, "CN=")
    }

    fun encrypt(plainText: String): String {
        return Cipher.getInstance(CIPHER_ALGORITHM).run {
            init(Cipher.ENCRYPT_MODE, keyPair.public)
            doFinal(plainText.toByteArray()).toHexString()
        }
    }

    fun decrypt(cipherText: String): String {
        return Cipher.getInstance(CIPHER_ALGORITHM).run {
            init(Cipher.DECRYPT_MODE, keyPair.private)
            val bytes = doFinal(cipherText.toHexStringByteArray())
            String(bytes)
        }
    }

    private fun KeyStore.getEntryKeyPair(alias: String): KeyPair? {
        val keyEntry = getEntry(alias, null) as? KeyStore.PrivateKeyEntry
        return keyEntry?.let { KeyPair(keyEntry.certificate.publicKey, keyEntry.privateKey) }
    }
}

private class SecureDataStore(
    private val alias: String,
) {

    private val keyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    fun setString(value: String) {
        generateKeyPair(alias, "CN=${value.toByteArrayHexString()}")
    }

    fun getString(): String? {
        val certificate = keyStore.getEntryX509Certificate(alias) ?: return null
        val value = certificate.subjectDN.toString().removePrefix("CN=")
        return String(value.toHexStringByteArray())
    }

    fun remove(): Boolean {
        return try {
            keyStore.deleteEntry(alias)
            true
        } catch (e: KeyStoreException) {
            false
        }
    }

    private fun KeyStore.getEntryX509Certificate(alias: String): X509Certificate? {
        val keyEntry = getEntry(alias, null) as? KeyStore.PrivateKeyEntry
        return keyEntry?.certificate as? X509Certificate
    }
}

private fun generateKeyPair(alias: String, principalName: String): KeyPair {
    val now = Date()
    val parameterSpec = KeyGenParameterSpec
        .Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
        .setKeyValidityStart(now)
        .setKeyValidityEnd(now.plusYears(DEFAULT_VALIDITY_PERIOD_YEAR))
        .setCertificateSerialNumber("1234567890".toBigInteger())
        .setCertificateSubject(
            X500Principal(principalName)
        )
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
        .build()
    return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE).run {
        initialize(parameterSpec)
        generateKeyPair()
    }
}

private fun String.toByteArrayHexString() = toByteArray().toHexString()

private fun String.toHexStringByteArray(): ByteArray {
    return ByteArray(length / 2) { index ->
        val stringIndex = 2 * index
        substring(stringIndex, stringIndex + 2).toShort(radix = 16).toByte()
    }
}