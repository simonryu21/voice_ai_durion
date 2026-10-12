package com.lgcns.codeengine.util

import android.util.Base64

object AESEncryptor {

    init {
        System.loadLibrary("aes-jni")
    }

    private external fun encrypt(data: ByteArray, length: Int): ByteArray

    private external fun decrypt(data: ByteArray, length: Int): ByteArray

    fun encrypt(str: String): String {
        val encByteArray = Base64.encodeToString(str.toByteArray(Charsets.UTF_16), Base64.DEFAULT).toByteArray() // 한글때문에 UTF_16으로 encoding
        return Base64.encodeToString(encrypt(encByteArray, encByteArray.size), Base64.DEFAULT) // byte array를 String을 올려주기위해 base64 encoding
    }

    fun decrypt(str: String): String {
        val byteArray = Base64.decode(str, Base64.DEFAULT)
        val decByteArray = decrypt(byteArray, byteArray.size)
        var idx = decByteArray.size -1
        while (idx > 0 && decByteArray[idx].toInt() == 0x00) idx-- // 뒤에 0x00인 부분은 제거하기 위해

        return String(Base64.decode(decByteArray.slice(0..idx).toByteArray(), Base64.DEFAULT), Charsets.UTF_16)
    }
}