package com.lgcns.codeengine.viewmodel

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.util.concurrent.ExecutionException
import javax.inject.Inject


open class QrReaderBaseViewModel @Inject constructor() : BaseViewModel() {
    private var cameraProviderLiveData: MutableLiveData<ProcessCameraProvider>? = null

    private val _isCameraReady = MutableStateFlow(false)
    val isCameraReady: StateFlow<Boolean> = _isCameraReady

    fun getProcessCameraProvider(context: Context): LiveData<ProcessCameraProvider?>? {
        if (cameraProviderLiveData == null) {
            cameraProviderLiveData = MutableLiveData()
            val cameraProviderFuture =
                ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    try {
                        cameraProviderLiveData?.value = cameraProviderFuture.get()
                        _isCameraReady.value = true
                    } catch (e: ExecutionException) {
                        // Handle any errors (including cancellation) here.
                        _isCameraReady.value = false
                        Timber.e(e)
                    } catch (e: InterruptedException) {
                        _isCameraReady.value = false
                        Timber.e(e)
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        }
        return cameraProviderLiveData
    }

}
