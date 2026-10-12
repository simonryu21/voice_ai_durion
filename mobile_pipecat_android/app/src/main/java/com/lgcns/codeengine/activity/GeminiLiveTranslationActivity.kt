package com.lgcns.codeengine.activity

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lgcns.codeengine.BuildConfig
import com.lgcns.codeengine.translation.data.audio.AudioPlayerManagerImpl
import com.lgcns.codeengine.translation.data.audio.AudioRecorderManagerImpl
import com.lgcns.codeengine.translation.data.audio.HeadsetManagerImpl
import com.lgcns.codeengine.translation.data.network.GeminiLiveServiceImpl
import com.lgcns.codeengine.translation.domain.usecase.InterpretationUseCaseImpl
import com.lgcns.codeengine.translation.presentation.GeminiLiveTranslationViewModel
import com.lgcns.codeengine.translation.ui.GeminiLiveTranslationApp
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit

class GeminiLiveTranslationActivity : ComponentActivity() {

    private lateinit var viewModel: GeminiLiveTranslationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create dependencies
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val okHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val headsetManager = HeadsetManagerImpl(audioManager)
        
        // Debug: Check API key
        val apiKey = BuildConfig.GEMINI_API_KEY
        Timber.d("API Key from BuildConfig: length=${apiKey.length}, isEmpty=${apiKey.isEmpty()}, isBlank=${apiKey.isBlank()}")
        if (apiKey.isBlank()) {
            Timber.e("GEMINI_API_KEY is not set in BuildConfig. Please check local.properties file and rebuild the project.")
        }
        
        val geminiLiveService = GeminiLiveServiceImpl(
            okHttpClient = okHttpClient,
            apiKey = apiKey
        )
        val audioRecorderManager = AudioRecorderManagerImpl(audioManager)
        val audioPlayerManager = AudioPlayerManagerImpl()

        val interpretationUseCase = InterpretationUseCaseImpl(
            headsetManager = headsetManager,
            geminiLiveService = geminiLiveService,
            audioRecorderManager = audioRecorderManager,
            audioPlayerManager = audioPlayerManager
        )

        viewModel = GeminiLiveTranslationViewModel(interpretationUseCase, headsetManager)

        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsState()

            GeminiLiveTranslationApp(
                uiState = uiState,
                onPermissionsGranted = viewModel::onPermissionsGranted,
                onPermissionsDenied = viewModel::onPermissionsDenied,
                onStartClick = viewModel::startInterpretation,
                onStopClick = {
                    viewModel.stopInterpretation()
                    finish() // Stop Session을 누르면 Activity 종료
                },
                onTargetLanguageChange = viewModel::setTargetLanguage,
                onCloseClick = { finish() }, // X 버튼을 누르면 Activity 종료
                onSendText = viewModel::sendTextInput
            )
        }
    }

    companion object {
        fun startActivity(context: Context) {
            val intent = Intent(context, GeminiLiveTranslationActivity::class.java)
            context.startActivity(intent)
        }
    }
}

