package com.lgcns.codeengine.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

open class BaseViewModel : ViewModel() {
    val showProgress = MutableLiveData<Boolean>()

    fun launchWithProgressBar(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch {
            showProgress()
            block.invoke(this)
            hideProgress()
        }
    }

    private fun showProgress() {
        showProgress.value = true
    }

    private fun hideProgress() {
        showProgress.value = false
    }
}