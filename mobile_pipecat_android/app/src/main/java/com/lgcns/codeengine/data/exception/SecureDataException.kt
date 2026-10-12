package com.lgcns.codeengine.data.exception

class SecureDataException(
    val error: SecureDataError,
    cause: Throwable? = null,
) : Exception("[${error.name}] ${cause?.message}", cause)

enum class SecureDataError {
    UNSUPPORTED,
    INVALID_STATE,
    INVALID_DATA,
    UNKNOWN,
}
