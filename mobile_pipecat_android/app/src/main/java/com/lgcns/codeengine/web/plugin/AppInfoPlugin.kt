package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.BuildConfig
import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.INVALID_ACTION
import com.lgcns.codeengine.web.params.JSCommand
import com.lgcns.codeengine.web.usecase.WebViewPluginUseCase
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

class AppInfoPlugin(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    private val webViewPluginUseCase: WebViewPluginUseCase =
        context.useCaseEntryPoint.provideWebViewPluginUseCase()

    override fun execute(jsCommand: JSCommand) {
        val requestId = jsCommand.requestId
        val params = JSONObject(jsCommand.params)
        when (jsCommand.action) {
            ACTION_GET_DEVICE_INFO -> getDeviceInfo(requestId, params)
            else -> sendJavaScript.error(
                requestId, code = INVALID_ACTION,
                message = "Invalid input action. [${jsCommand.action}]"
            )
        }
    }

    private fun getDeviceInfo(requestId: String, params: JSONObject) {
        val needPhoneNo = params.optBoolean("needPhoneNo", false)

        context.scope.launch {
            val result = JSONObject().apply {
                put("BUILD_DATE", BuildConfig.BUILD_DATE) // 앱 빌드 일자.
                put("DEVICE_ID", webViewPluginUseCase.deviceRepository.getDeviceId())
                put("APP_VERSION", webViewPluginUseCase.deviceRepository.getAppVersion()) // 앱 버전
                put("OS_TYPE", "A")  // I : 아이폰 , A : 안드로이드
                put("PHONE_OS", android.os.Build.VERSION.RELEASE) // 핸드폰 OS 버전
                put("PHONE_COM", webViewPluginUseCase.deviceRepository.getSimOperator()) // 통신사 정보 (이름)
                put("PHONE_MODEL", android.os.Build.MODEL) // 핸드폰 모델정보
                put("PUSH_TOKEN", webViewPluginUseCase.deviceRepository.getPushTokenOrNull()) // 푸시토큰 정보
                put("PHONE_NO", if (needPhoneNo) getPhoneNumber() else "") // 폰번호
                put("SERVER_MODE", BuildConfig.SERVER_TYPE) // 서버모드
            }
            sendJavaScript.success(requestId, result)
        }

    }

    private suspend fun getPhoneNumber(): String {
        return if (context.activity.pluginPermissions.phoneNumberPermission.grant()) {
            webViewPluginUseCase.deviceRepository.getPhoneNumber()
        } else {
            Timber.w("Denied read phone number permission.")
            ""
        }
    }

    companion object {
        const val SERVICE_NAME = "AppInfo"

        private const val ACTION_GET_DEVICE_INFO = "getDeviceInfo"
    }
}