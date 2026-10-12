package com.lgcns.codeengine.viewmodel

import androidx.lifecycle.MutableLiveData
import com.lgcns.codeengine.data.Result
import com.lgcns.codeengine.data.entity.Auth
import com.lgcns.codeengine.data.repository.CodeEngineRepository
import com.lgcns.codeengine.data.repository.remote.model.LoginResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
): BaseViewModel() {

    @Inject
    lateinit var repository: CodeEngineRepository

    val loginResponse = MutableLiveData<Result<LoginResponse>>()

    fun login(auth: Auth) {
        launchWithProgressBar {
            loginResponse.value = repository.login(auth)
        }
    }
}