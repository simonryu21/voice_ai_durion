package com.lgcns.codeengine.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.lgcns.codeengine.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : FCMSettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
    }
}