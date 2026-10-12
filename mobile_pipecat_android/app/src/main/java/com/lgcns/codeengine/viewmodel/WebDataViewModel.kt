package com.lgcns.codeengine.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WebDataViewModel @Inject constructor() : ViewModel() {
    private var webJsonData: JSONObject = JSONObject()
    private val webVariable = mutableMapOf<String, String?>()

    private val _progress = MutableStateFlow(false to "")
    val progress = _progress.asStateFlow()
    val isLoading: Boolean get() = _progress.value.first

    fun getWebJsonData(): JSONObject {
        return webJsonData
    }

    fun setWebJsonData(data: JSONObject) {
        webJsonData = data
    }

    fun getWebValue(keys: List<String>): Map<String, String?> {
        val result = mutableMapOf<String, String?>()
        keys.forEach { key ->
            result[key] = webVariable[key]
        }
        return result
    }

    fun setWebValue(records: Map<String, String?>) {
        records.forEach { (key, value) ->
            if (value != null) {
                webVariable[key] = value
            } else {
                webVariable.remove(key)
            }
        }
    }

    fun setProgress(isLoading: Boolean, message: String = "") {
        Timber.d("setProgress() $isLoading")
        viewModelScope.launch {
            _progress.emit(isLoading to message)
        }
    }

}