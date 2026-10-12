package com.lgcns.codeengine.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository
import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository.Companion.TOKEN_KEY
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TokenViewModel @Inject constructor(
    private val dataStoreRepository: DataStoreRepository
): ViewModel() {

    val token = MutableLiveData<String?>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.getDataStoreString(TOKEN_KEY).collect {
                withContext(Dispatchers.Main) {
                    token.value = it
                }
            }
        }
    }

    fun saveToken(token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.setDataStoreString(TOKEN_KEY, token)
        }
    }

    fun deleteToken() {
        viewModelScope.launch(Dispatchers.IO) {
            dataStoreRepository.removeDataStoreString(TOKEN_KEY)
        }
    }

    suspend fun getToken() : String {
        return dataStoreRepository.getDataStoreStringOnce(TOKEN_KEY) ?: ""
    }

}