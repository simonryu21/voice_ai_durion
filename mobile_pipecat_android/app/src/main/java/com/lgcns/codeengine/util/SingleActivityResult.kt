package com.lgcns.codeengine.util

import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract

fun <I, O> ActivityResultCaller.registerForSingleActivityResult(
    contract: ActivityResultContract<I, O>,
): SingleActivityResultLauncher<I, O> {
    val callback = SingleActivityResultCallback<O>()
    val launcher: ActivityResultLauncher<I> = registerForActivityResult(contract, callback)
    return SingleActivityResultLauncherImpl(launcher, callback)
}

interface SingleActivityResultLauncher<I, O> {
    fun launch(input: I, onResult: (O) -> Unit)
}

private class SingleActivityResultLauncherImpl<I, O>(
    private val launcher: ActivityResultLauncher<I>,
    private val callback: SingleActivityResultCallback<O>,
) : SingleActivityResultLauncher<I, O> {
    override fun launch(input: I, onResult: (O) -> Unit) {
        callback.delegate = onResult
        launcher.launch(input)
    }
}

private class SingleActivityResultCallback<O> : ActivityResultCallback<O> {
    var delegate: ((O) -> Unit)? = null
    override fun onActivityResult(result: O) {
        delegate?.invoke(result)
        delegate = null
    }
}