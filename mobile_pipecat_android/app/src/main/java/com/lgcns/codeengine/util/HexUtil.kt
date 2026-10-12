package com.lgcns.codeengine.util

import java.util.*

object HexUtil {
    fun hexToByte(hexString: String): ByteArray {
        val len = hexString.length / 2
        val result = ByteArray(len)
        for (i in 0 until len) {
            result[i] = Integer.valueOf(hexString.substring(2 * i, 2 * i + 2), 16).toByte()
        }
        return result
    }

    fun hexToString(bytes: ByteArray): String {
        val formatter = Formatter()
        for (b in bytes) {
            formatter.format("%02x", b)
        }
        return formatter.toString()
    }

    fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((((s[i].digitToIntOrNull(16) ?: (-1 shl 4)) + s[i + 1].digitToIntOrNull(
                16
            )!!) ?: -1)).toByte()
            i += 2
        }
        return data
    }
}
