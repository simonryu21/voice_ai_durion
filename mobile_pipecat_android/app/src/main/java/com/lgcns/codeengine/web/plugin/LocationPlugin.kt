package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.data.repository.location.CurrentLocationResult
import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.JSCommand
import com.lgcns.codeengine.web.usecase.WebViewPluginUseCase
import kotlinx.coroutines.*
import org.json.JSONObject

class LocationPlugin constructor(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    private val webViewPluginUseCase: WebViewPluginUseCase =
        context.useCaseEntryPoint.provideWebViewPluginUseCase()

    override fun execute(jsCommand: JSCommand) {
        val requestId = jsCommand.requestId
        when (jsCommand.action) {
            ACTION_GET_CURRENT_LOCATION -> getCurrentLocation(requestId)
            ACTION_IS_PERMISSION_FOR_LOCATION -> isPermissionForLocation(requestId)
            else -> sendJavaScript.error(
                requestId, code = "INVALID_ACTION",
                message = "Unsupported action. [${jsCommand.action}]"
            )
        }
    }

    private fun getCurrentLocation(requestId: String) {
        context.scope.launch {
            if (!context.activity.pluginPermissions.locationPermission.grant()) {
                sendJavaScript.error(
                    requestId, code = "PERMISSION_ERROR",
                    message = "Denied permission to access geo location."
                )
                return@launch
            }

            when (val result = webViewPluginUseCase.locationRepository.getCurrentLocation()) {
                is CurrentLocationResult.Success -> sendJavaScript.success(requestId) {
                    put("latitude", result.location.latitude.toString())
                    put("longitude", result.location.longitude.toString())
                }

                CurrentLocationResult.LocationDisabled -> sendJavaScript.error(
                    requestId, code = "LOCATION_DISABLED",
                    message = "Disabled location settings."
                )
                CurrentLocationResult.NoPermission -> sendJavaScript.error(
                    requestId, code = "PERMISSION_ERROR",
                    message = "No permission to access geo location."
                )
                CurrentLocationResult.Timeout -> sendJavaScript.error(
                    requestId, code = "LOCATION_FAILED",
                    message = "Timed out wait find geo location."
                )
            }
        }
    }

    private fun isPermissionForLocation(requestId: String) {
        context.scope.launch {
            val resultCode : String = if (webViewPluginUseCase.locationRepository.hasAvailableProvider()) RETURN_OK else RETURN_ERROR
            sendJavaScript(requestId, resultCode, JSONObject())
        }
    }

    companion object {
        const val SERVICE_NAME = "Location"

        private const val ACTION_GET_CURRENT_LOCATION = "getCurrentLocation"
        private const val ACTION_IS_PERMISSION_FOR_LOCATION = "isPermissionForLocation"
    }
}