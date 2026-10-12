package com.lgcns.codeengine.web

import android.webkit.JavascriptInterface
import com.lgcns.codeengine.web.params.JSCommand
import com.lgcns.codeengine.web.plugin.AppHandoffPlugin
import com.lgcns.codeengine.web.plugin.AppInfoPlugin
import com.lgcns.codeengine.web.plugin.BarcodeDetectPlugin
import com.lgcns.codeengine.web.plugin.BarcodePlugin
import com.lgcns.codeengine.web.plugin.BasePlugin.Companion.RETURN_ERROR
import com.lgcns.codeengine.web.plugin.CachePlugin
import com.lgcns.codeengine.web.plugin.ClipboardPlugin
import com.lgcns.codeengine.web.plugin.CryptoPlugin
import com.lgcns.codeengine.web.plugin.FileSelectPlugin
import com.lgcns.codeengine.web.plugin.FingerprintPlugin
import com.lgcns.codeengine.web.plugin.ImageEditPlugin
import com.lgcns.codeengine.web.plugin.ImageViewerPlugin
import com.lgcns.codeengine.web.plugin.JWTAuthPlugin
import com.lgcns.codeengine.web.plugin.KeyboardPlugin
import com.lgcns.codeengine.web.plugin.LiveTranslationPlugin
import com.lgcns.codeengine.web.plugin.PipecatPreviewPlugin
import com.lgcns.codeengine.web.plugin.LocationPlugin
import com.lgcns.codeengine.web.plugin.NavigatorPlugin
import com.lgcns.codeengine.web.plugin.NetworkPlugin
import com.lgcns.codeengine.web.plugin.NfcTagPlugin
import com.lgcns.codeengine.web.plugin.PIPPlugin
import com.lgcns.codeengine.web.plugin.PermissionPlugin
import com.lgcns.codeengine.web.plugin.ProgressBarPlugin
import com.lgcns.codeengine.web.plugin.SMSPlugin
import com.lgcns.codeengine.web.plugin.SecureDataPlugin
import com.lgcns.codeengine.web.plugin.SharePlugin
import com.lgcns.codeengine.web.plugin.SmsReceiverPlugin
import com.lgcns.codeengine.web.plugin.ToastPlugin
import com.lgcns.codeengine.web.plugin.UserPrefPlugin
import com.lgcns.codeengine.web.plugin.WebPopupPlugin
import com.lgcns.codeengine.web.plugin.WebViewDataPlugin
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import org.json.JSONObject
import timber.log.Timber

class WebAppBridge(
    private val context: PluginContext,
) {

    private val userPrefPlugin: UserPrefPlugin by lazy {
        UserPrefPlugin(context)
    }

    private val webViewDataPlugin: WebViewDataPlugin by lazy {
        WebViewDataPlugin(context)
    }

    private val locationPlugin: LocationPlugin by lazy {
        LocationPlugin(context)
    }

    private val toastPlugin: ToastPlugin by lazy {
        ToastPlugin(context)
    }

    private val webPopupPlugin: WebPopupPlugin by lazy {
        WebPopupPlugin(context)
    }

    private val keyboardPlugin: KeyboardPlugin by lazy {
        KeyboardPlugin(context)
    }

    private val cachePlugin: CachePlugin by lazy {
        CachePlugin(context)
    }

    private val smsPlugin: SMSPlugin by lazy {
        SMSPlugin(context)
    }

    private val securedDataPlugin: SecureDataPlugin by lazy {
        SecureDataPlugin(context)
    }

    private val cryptoPlugin: CryptoPlugin by lazy {
        CryptoPlugin(context)
    }

    private val navigatorPlugin: NavigatorPlugin by lazy {
        NavigatorPlugin(context)
    }

    private val appInfoPlugin: AppInfoPlugin by lazy {
        AppInfoPlugin(context)
    }

    private val progressBarPlugin: ProgressBarPlugin by lazy {
        ProgressBarPlugin(context)
    }

    private val permissionPlugin: PermissionPlugin by lazy {
        PermissionPlugin(context)
    }

    private val sharePlugin: SharePlugin by lazy {
        SharePlugin(context)
    }

    private val barcodePlugin: BarcodePlugin by lazy {
        BarcodePlugin(context)
    }

    private val imageEditPlugin: ImageEditPlugin by lazy {
        ImageEditPlugin(context)
    }

    private val pipPlugin: PIPPlugin by lazy {
        PIPPlugin(context)
    }

    private val clipboardPlugin: ClipboardPlugin by lazy {
        ClipboardPlugin(context)
    }

    private val jwtAuthPlugin: JWTAuthPlugin by lazy {
        JWTAuthPlugin(context)
    }

    private val imageViewerPlugin: ImageViewerPlugin by lazy {
        ImageViewerPlugin(context)
    }

    private val fingerprintPlugin: FingerprintPlugin by lazy {
        FingerprintPlugin(context)
    }

    private val fileSelectPlugin: FileSelectPlugin by lazy {
        FileSelectPlugin(context)
    }

    private val barcodeDetectPlugin: BarcodeDetectPlugin by lazy {
        BarcodeDetectPlugin(context)
    }

    private val nfcTagPlugin: NfcTagPlugin by lazy {
        NfcTagPlugin(context)
    }

    private val networkPlugin: NetworkPlugin by lazy {
        NetworkPlugin(context)
    }

    private val smsReceiverPlugin: SmsReceiverPlugin by lazy {
        SmsReceiverPlugin(context)
    }

    private val liveTranslationPlugin: LiveTranslationPlugin by lazy {
        LiveTranslationPlugin(context)
    }

    private val pipecatPreviewPlugin: PipecatPreviewPlugin by lazy {
        PipecatPreviewPlugin(context)
    }

    private val appHandoffPlugin: AppHandoffPlugin by lazy {
        AppHandoffPlugin(context)
    }

    /**
     * [Plugin 초기화]
     * plugin.js plugin()
     * plugin.js pluginService.addItem()
     *
     * [호출 및 응답]
     * pluginService.postCommand() ->
     * plugin.js BRIDGE_NAME(CodeEngineBridge).postMessage() ->
     * WebAppBridge.postMessage() ->
     * ###Plugin.execute() ->
     * ###Activity.sendJavaScript() ->
     * plugin.js callbackListener() ->
     * plugin.js pluginService.dispatchResult() ->
     * pluginService.postCommand( , function () { ... })
     *
     * [API 추가시 추가해야 하는 것들]
     * plugin.js plugin(),
     * WebAppBridge.postMessage(),
     * ###Plugin,
     */
    @JavascriptInterface
    fun postMessage(strParams: String) {
        Timber.d("postMessage strParams:$strParams")

        try {
            val command = context.json.decodeFromString<JSCommand>(strParams)

            Timber.d("command.serviceName=${command.serviceName}, action=${command.action}")
            when (command.serviceName) {
                UserPrefPlugin.SERVICE_NAME -> userPrefPlugin.execute(command)
                WebViewDataPlugin.SERVICE_NAME -> webViewDataPlugin.execute(command)
                LocationPlugin.SERVICE_NAME -> locationPlugin.execute(command)
                ToastPlugin.SERVICE_NAME -> toastPlugin.execute(command)
                WebPopupPlugin.SERVICE_NAME -> webPopupPlugin.execute(command)
                KeyboardPlugin.SERVICE_NAME -> keyboardPlugin.execute(command)
                CachePlugin.SERVICE_NAME -> cachePlugin.execute(command)
                SMSPlugin.SERVICE_NAME -> smsPlugin.execute(command)
                SecureDataPlugin.SERVICE_NAME -> securedDataPlugin.execute(command)
                CryptoPlugin.SERVICE_NAME -> cryptoPlugin.execute(command)
                NavigatorPlugin.SERVICE_NAME -> navigatorPlugin.execute(command)
                AppInfoPlugin.SERVICE_NAME -> appInfoPlugin.execute(command)
                ProgressBarPlugin.SERVICE_NAME -> progressBarPlugin.execute(command)
                PermissionPlugin.SERVICE_NAME -> permissionPlugin.execute(command)
                SharePlugin.SERVICE_NAME -> sharePlugin.execute(command)
                BarcodePlugin.SERVICE_NAME -> barcodePlugin.execute(command)
                ImageEditPlugin.SERVICE_NAME -> imageEditPlugin.execute(command)
                PIPPlugin.SERVICE_NAME -> pipPlugin.execute(command)
                ClipboardPlugin.SERVICE_NAME -> clipboardPlugin.execute(command)
                JWTAuthPlugin.SERVICE_NAME -> jwtAuthPlugin.execute(command)
                ImageViewerPlugin.SERVICE_NAME -> imageViewerPlugin.execute(command)
                FingerprintPlugin.SERVICE_NAME -> fingerprintPlugin.execute(command)
                FileSelectPlugin.SERVICE_NAME -> fileSelectPlugin.execute(command)
                BarcodeDetectPlugin.SERVICE_NAME -> barcodeDetectPlugin.execute(command)
		        NfcTagPlugin.SERVICE_NAME -> nfcTagPlugin.execute(command)
                NetworkPlugin.SERVICE_NAME -> networkPlugin.execute(command)
                SmsReceiverPlugin.SERVICE_NAME -> smsReceiverPlugin.execute(command)
                LiveTranslationPlugin.SERVICE_NAME -> liveTranslationPlugin.execute(command)
                PipecatPreviewPlugin.SERVICE_NAME -> pipecatPreviewPlugin.execute(command)
                AppHandoffPlugin.SERVICE_NAME -> appHandoffPlugin.execute(command)
                else -> context.activity.sendJavaScript(command.requestId, RETURN_ERROR, JSONObject())
            }
        } catch (e: SerializationException) {
            Timber.e(e)
        }
    }
}
