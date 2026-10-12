package com.lgcns.codeengine.viewmodel

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lgcns.codeengine.data.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
): BaseViewModel() {

    enum class AppStartStep(val value: Int) {
        BEGIN(0),
        // Main 화면을 보이기 전에 필요한 STEP들을 추가합니다.
        CHECK_SOMETHING(1),
        GO_NEXT_ACTIVITY(2);

        companion object {
            fun fromInt(value: Int) = AppStartStep.values().first { it.value == value }
        }
    }

    private var currentStep = AppStartStep.BEGIN

    val checkSomething = MutableLiveData<Result<Any>>()

    fun checkSomething() {
        viewModelScope.launch(Dispatchers.IO) {
            //@todo 버전 업데이트 조회 등을 합니다. 테스트를 위해 1초간 wait 합니다.
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    checkSomething.postValue(Result.Success(true))
                },
                1000 // value in milliseconds
            )
        }
    }

    fun getNextStep(): AppStartStep {
        currentStep = try {
            AppStartStep.fromInt(currentStep.value + 1)
        } catch (e: Exception) {
            Timber.e(e)
            AppStartStep.GO_NEXT_ACTIVITY
        }
        return currentStep
    }

}