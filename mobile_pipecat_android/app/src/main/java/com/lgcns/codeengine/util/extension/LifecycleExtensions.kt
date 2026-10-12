package com.lgcns.codeengine.util.extension

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch

fun <T> Flow<T>.collectRepeatOnStarted(
    owner: LifecycleOwner,
    collector: FlowCollector<T>
) {
    owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            collect(collector)
        }
    }
}

fun <T> Flow<T>.collectRepeatOnStartedWhile(
    owner: LifecycleOwner,
    doWhile: () -> (Boolean),
    collector: FlowCollector<T>,
) {
    owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            collect(
                DoWhileFlowCollector(
                    cancelScope = this@launch,
                    collector = collector,
                    doWhile = doWhile,
                )
            )
        }
    }
}

private class DoWhileFlowCollector<T>(
    private val cancelScope: CoroutineScope,
    private val collector: FlowCollector<T>,
    private val doWhile: () -> Boolean,
) : FlowCollector<T> {
    override suspend fun emit(value: T) {
        collector.emit(value)
        if (!doWhile.invoke()) {
            cancelScope.cancel()
        }
    }
}
