package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.web.params.*
import org.json.JSONObject

private typealias SendJavaScript = (requestId: String, resultCode: String, result: JSONObject) -> Unit

abstract class BasePlugin {
    abstract fun execute(jsCommand: JSCommand)

    protected fun SendJavaScript.success(
        requestId: String,
        result: JSONObject = JSONObject(),
        builderAction: (JSONObject.() -> Unit)? = null
    ) {
        builderAction?.invoke(result)
        this.invoke(requestId, RETURN_OK, result)
    }

    protected fun SendJavaScript.error(
        requestId: String,
        code: String,
        message: String,
        builderAction: (JSONObject.() -> Unit)? = null
    ) {
        val result = JSONObject()
            .put(PARAM_ERROR_CODE, code)
            .put(PARAM_ERROR_MESSAGE, message)
        builderAction?.invoke(result)
        this.invoke(requestId, RETURN_ERROR, result)
    }

    fun sendInvalidAction(): JSONObject {
        return JSONObject().put(PARAM_ERROR_CODE, INVALID_ACTION)
            .put(PARAM_ERROR_MESSAGE, INVALID_INPUT_ACTION)
    }

    fun sendInvalidParams(): JSONObject {
        return JSONObject().put(PARAM_ERROR_CODE, INVALID_PARAMS)
            .put(PARAM_ERROR_MESSAGE, INVALID_INPUT_PARAMS)
    }

    companion object {
        const val RETURN_OK = "200"
        const val RETURN_ERROR = "500"

        // Web App단과 협의하여 Error Code를 여기에 추가할 수 있습니다.

    }
}