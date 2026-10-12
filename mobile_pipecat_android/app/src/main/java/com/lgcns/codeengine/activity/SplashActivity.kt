package com.lgcns.codeengine.activity

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.lgcns.codeengine.BuildConfig
import com.lgcns.codeengine.R
import com.lgcns.codeengine.data.Result
import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository
import com.lgcns.codeengine.databinding.ActivitySplashBinding
import com.lgcns.codeengine.ui.CustomDialogFragment
import com.lgcns.codeengine.ui.service.ForegroundAppService
import com.lgcns.codeengine.util.SecurityUtils
import com.lgcns.codeengine.viewmodel.SplashViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.security.MessageDigest
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var dataStoreRepository: DataStoreRepository

    private lateinit var mainBinding: ActivitySplashBinding

    private val viewModel: SplashViewModel by lazy {
        ViewModelProvider(this)[SplashViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainBinding = DataBindingUtil.setContentView(this, R.layout.activity_splash)

        // remote에 있는 image 파일을 로딩합니다.
        // 실패시에는 기본 이미지인 "@drawable/default_splash_img"이 보입니다.
        // 한 번 로딩한 후에는 cache에 저장하고 URL이 같으면 cache의 내용으로 loading합니다. (diskCacheStrategy(DiskCacheStrategy.ALL))
        // image size는 center에 맞추고 비율에 맞지 않는 것은 crop합니다.
        Glide.with(this)
            .load("http://img.ds.shinsegae.com/images/bg/20200925134010420.jpg")
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .centerCrop()
            .into(mainBinding.ivSplash)

        if (checkAppSecurity()) {
            setObservers()

            // Foreground Service 시작 (상태바에 항상 표시)
            ForegroundAppService.startService(this)

            goNext()
        }

    }

    private fun goNext() {
        val nextStep = viewModel.getNextStep()
        Timber.d("The next step is $nextStep")
        when (nextStep) {
            SplashViewModel.AppStartStep.CHECK_SOMETHING -> viewModel.checkSomething()
            SplashViewModel.AppStartStep.GO_NEXT_ACTIVITY -> goNextActivity()
            else -> Timber.e("Unhandled Case: $nextStep")
        }
    }

    private fun goNextActivity() {
        lifecycleScope.launch {
            val shownPermissionNotice =
                dataStoreRepository.getDataStoreBooleanOnce(DataStoreRepository.SHOWN_PERMISSION_NOTICE)
                    ?: false
            if (shownPermissionNotice) {
                //val intent = Intent(this@SplashActivity, LoginActivity::class.java)
                val intent = Intent(this@SplashActivity, WebViewTestActivity::class.java)
                startActivity(intent)
            } else {
                val intent = Intent(this@SplashActivity, PermissionNoticeActivity::class.java)
                startActivity(intent)
            }

            finish()
        }
    }

    private fun setObservers() {

        // 버전 업그레이드 등을 체크합니다.
        viewModel.checkSomething.observe(this) {
            Timber.d("checkSomething is observed.")
            it.let {
                when (it) {
                    is Result.Success -> {
                        Timber.d("check something is OK.")
                        goNext()
                    }

                    is Result.Error -> {
                        Timber.e(it.exception)
                        goNext()
                    }
                }
            }
        }
    }

    private fun showCustomConfirmDialog(
        message: String?,
        result: BaseWebViewActivity.ConfirmResult?
    ): CustomDialogFragment {
        CustomDialogFragment.Builder().apply {
            setMessage(message ?: "")
            setPositiveButton(getString(R.string.msg_close)) { result?.confirm() }

            setCancelable(false)
            return show(supportFragmentManager)
        }
    }

    private fun checkAppSecurity(): Boolean {
        if (SecurityUtils.checkRooting(this)) {
            showCustomConfirmDialog(getString(R.string.str_rootingcheck_msg),
                object : BaseWebViewActivity.ConfirmResult {
                    override fun confirm() {
                        Timber.d("confirm()")
                        finishAffinity()
                    }

                    override fun cancel() {}
                })
            return false
        }

        if (!checkSigningHashKey()) {
            showCustomConfirmDialog(getString(R.string.str_different_key_msg),
                object : BaseWebViewActivity.ConfirmResult {
                    override fun confirm() {
                        Timber.d("confirm()")
                        finishAffinity()
                    }

                    override fun cancel() {}
                })
            return false
        }

        if (!checkPackageName()) {
            showCustomConfirmDialog(getString(R.string.str_different_package_name_msg),
                object : BaseWebViewActivity.ConfirmResult {
                    override fun confirm() {
                        Timber.d("confirm()")
                        finishAffinity()
                    }

                    override fun cancel() {}
                })
            return false
        }

        return true
    }

    private fun checkPackageName(): Boolean {
        Timber.d("packageName=$packageName")
        return (packageName == COMP_PACKAGE_NAME)
    }

    private fun checkSigningHashKey(): Boolean {
        @Suppress("DEPRECATION")
        val signatureString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val packageInfo =
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signature = packageInfo.signingInfo.apkContentsSigners[0]
            val messageDigest = MessageDigest.getInstance("SHA")
            messageDigest.update(signature.toByteArray())
            String(Base64.encode(messageDigest.digest(), Base64.DEFAULT))
        } else {
            val packageInfo =
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            val signature = packageInfo.signatures[0]
            val messageDigest = MessageDigest.getInstance("SHA")
            messageDigest.update(signature.toByteArray())
            String(Base64.encode(messageDigest.digest(), Base64.DEFAULT))
        }

        if (BuildConfig.DEBUG) return true

        return (signatureString == RELEASE_SIGNING_HASH_KEY)
    }

    private fun now(): Date = Calendar.getInstance().time

    companion object {
        /**
         * @note 앱 빌드하는 컴퓨터에서 keytool 실행 후 해당 key로 아래 HASH KEY를 변경해야 함.
         * Reference: https://velog.io/@iamjm29/Android-%ED%82%A4%ED%95%B4%EC%8B%9Ckey-hash-%EC%B6%94%EC%B6%9C%ED%95%98%EA%B8%B0
         * > keytool -exportcert -alias androiddebugkey -keystore debug.keystore.jks -storepass android -keypass android | openssl sha1 -binary | openssl base64
         * > keytool -exportcert -alias focusone -keystore SinSGkey_focus0504 -storepass focus0504 -keypass focus0504 | openssl sha1 -binary | openssl base64
         */
        const val RELEASE_SIGNING_HASH_KEY = "KR4Bpijzcew9qLDyRXWTRPl1R1w=\n" // release.keystore.jks

        const val COMP_PACKAGE_NAME = "com.lgcns.codeengine"
    }

}