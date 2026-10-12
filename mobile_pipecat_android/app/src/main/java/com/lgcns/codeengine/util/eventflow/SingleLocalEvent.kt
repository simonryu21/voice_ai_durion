package com.lgcns.codeengine.util.eventflow


object SingleLocalEvent {
    private val _dataEvent = MutableEventFlow<String>()
    val dataEvent = _dataEvent.asEventFlow()

    suspend fun sendEvent(data: String) {
        _dataEvent.emit(data)
    }
}