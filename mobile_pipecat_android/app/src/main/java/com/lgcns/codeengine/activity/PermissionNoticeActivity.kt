package com.lgcns.codeengine.activity

import android.app.AppOpsManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.lgcns.codeengine.BuildConfig
import com.lgcns.codeengine.R
import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository
import com.lgcns.codeengine.databinding.ActivityPermissionNoticeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.lgcns.codeengine.activity.BaseWebViewActivity.Companion.LOAD_URL

@AndroidEntryPoint
class PermissionNoticeActivity : AppCompatActivity() {

    private lateinit var mainBinding: ActivityPermissionNoticeBinding

    @Inject
    lateinit var dataStoreRepository: DataStoreRepository

    private var waitingForUsageStatsPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainBinding = DataBindingUtil.setContentView(this, R.layout.activity_permission_notice)

        mainBinding.tvConfirm.setOnClickListener {
            if (!isUsageStatsGranted()) {
                waitingForUsageStatsPermission = true
                Timber.d("Usage stats permission not granted, opening settings")
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } else {
                proceedToNextActivity()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (waitingForUsageStatsPermission) {
            waitingForUsageStatsPermission = false
            proceedToNextActivity()
        }
    }

    private fun isUsageStatsGranted(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun proceedToNextActivity() {
        lifecycleScope.launch {
            dataStoreRepository.setDataStoreBoolean(
                DataStoreRepository.SHOWN_PERMISSION_NOTICE, true
            )

            val intent = Intent(this@PermissionNoticeActivity, WebViewTestActivity::class.java)
            intent.putExtra(LOAD_URL, BuildConfig.WEBVIEW_PLUGIN_TEST)
            startActivity(intent)
            finish()
        }
    }
}