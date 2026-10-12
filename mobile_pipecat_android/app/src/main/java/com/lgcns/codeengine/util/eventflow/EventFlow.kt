package com.lgcns.codeengine.util.eventflow

import kotlinx.coroutines.flow.Flow

class EventFlow<T>(flow: Flow<T>) : Flow<T> by flow

fun <T> MutableEventFlow<T>.asEventFlow(): EventFlow<T> = EventFlow(this)
