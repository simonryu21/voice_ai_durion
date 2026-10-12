package com.lgcns.codeengine.util.eventflow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow

const val DEFAULT_REPEAT = 1

class MutableEventFlow<T>(
    replay: Int = DEFAULT_REPEAT,
) : Flow<T>, FlowCollector<T> {
    private val flow: MutableSharedFlow<EventWrapper<T>> = MutableSharedFlow(replay = replay)

    override suspend fun collect(collector: FlowCollector<T>) = flow
        .collect { slot ->
            if (!slot.markConsumed()) {
                collector.emit(slot.value)
            }
        }

    override suspend fun emit(value: T) {
        flow.emit(EventWrapper(value))
    }
}