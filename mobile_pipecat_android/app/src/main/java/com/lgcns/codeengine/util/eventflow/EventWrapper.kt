package com.lgcns.codeengine.util.eventflow

import java.util.concurrent.atomic.AtomicBoolean

internal class EventWrapper<T>(val value: T) {
    private val isHandled: AtomicBoolean = AtomicBoolean(false)

    fun markConsumed(): Boolean = isHandled.getAndSet(true)
}