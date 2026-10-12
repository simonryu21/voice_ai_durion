package com.lgcns.codeengine.viewmodel

import com.lgcns.codeengine.data.Result
import androidx.lifecycle.MutableLiveData
import com.lgcns.codeengine.data.repository.CodeEngineRepository
import com.lgcns.codeengine.data.repository.remote.model.UserInfoResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
): BaseViewModel() {

    @Inject
    lateinit var repository: CodeEngineRepository

     val userInfoResponse = MutableLiveData<Result<UserInfoResponse>>()

    fun getUserInfo() {
        launchWithProgressBar {
            userInfoResponse.value = repository.getUserInfo()
        }
    }
}