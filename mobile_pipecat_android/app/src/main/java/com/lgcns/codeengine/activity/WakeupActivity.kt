package com.lgcns.codeengine.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.lgcns.codeengine.R
import java.util.*

class WakeupActivity : AppCompatActivity() {
    
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var toggleButton: Button
    
    private var isListening = false
    // Wake word "DuriOn" + STT가 들려줄 수 있는 음차 변형들. 하나라도 포함되면 감지.
    private val WAKE_WORDS = listOf(
        "DuriOn", "두리온", "듀리온", "듀리언", "두리언", "리언"
    )
    private val RECORD_AUDIO_PERMISSION_CODE = 1
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)
        toggleButton = findViewById(R.id.toggleButton)
        
        // 권한 확인
        checkPermission()
        
        // SpeechRecognizer 초기화
        initializeSpeechRecognizer()
        
        toggleButton.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }
    }
    
    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        }
    }
    
    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "음성 인식을 사용할 수 없습니다", Toast.LENGTH_SHORT).show()
            return
        }
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                runOnUiThread {
                    statusText.text = "🎤 듣는 중..."
                }
            }
            
            override fun onBeginningOfSpeech() {}
            
            override fun onRmsChanged(rmsdB: Float) {}
            
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {}
            
            override fun onError(error: Int) {
                val shouldShowError = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> false
                    else -> true
                }
                
                if (shouldShowError) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_NETWORK -> "네트워크 오류"
                        SpeechRecognizer.ERROR_CLIENT -> "클라이언트 오류"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "권한 없음"
                        else -> "오류: $error"
                    }
                    
                    runOnUiThread {
                        resultText.text = errorMessage
                    }
                }
                
                restartListening()
            }
            
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    val recognizedText = matches[0]
                    
                    runOnUiThread {
                        resultText.text = "인식: $recognizedText"

                        if (WAKE_WORDS.any { recognizedText.contains(it, ignoreCase = true) }) {
                            onWakeWordDetected()
                        }
                    }
                }
                
                restartListening()
            }
            
            private fun restartListening() {
                if (isListening) {
                    android.os.Handler(mainLooper).postDelayed({
                        if (isListening) {
                            speechRecognizer.startListening(recognizerIntent)
                        }
                    }, 100)
                }
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    val partialText = matches[0]
                    
                    runOnUiThread {
                        resultText.text = "부분 인식: $partialText"

                        if (WAKE_WORDS.any { partialText.contains(it, ignoreCase = true) }) {
                            onWakeWordDetected()
                        }
                    }
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }
    
    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "마이크 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            return
        }
        
        isListening = true
        toggleButton.text = "중지"
        statusText.text = "🎤 'DuriOn'을 말해보세요"
        resultText.text = ""
        
        speechRecognizer.startListening(recognizerIntent)
    }
    
    private fun stopListening() {
        isListening = false
        toggleButton.text = "시작"
        statusText.text = "⏸ 대기 중"
        
        speechRecognizer.stopListening()
    }
    
    private fun onWakeWordDetected() {
        statusText.text = "✅ DuriOn 감지됨!"
        Toast.makeText(this, "DuriOn이 호출되었습니다!", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "권한이 승인되었습니다", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "권한이 필요합니다", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
