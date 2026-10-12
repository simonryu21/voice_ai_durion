package com.lgcns.codeengine.util.extension

import timber.log.Timber
import java.util.*


fun ByteArray.toHexString(): String {
    val builder = Formatter()
    this.forEach { builder.format("%02x", it) }
    return builder.toString()
}

fun String.decodeHexToByteArray(): ByteArray? {
    return try {
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    } catch (e: Exception) {
        Timber.e("Failed to decode hex string to byte array.")
        null
    }
}