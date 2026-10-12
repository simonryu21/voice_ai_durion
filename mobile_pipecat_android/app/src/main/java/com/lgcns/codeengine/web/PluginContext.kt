package com.lgcns.codeengine.web

import androidx.lifecycle.lifecycleScope
import com.lgcns.codeengine.activity.BaseWebViewActivity
import com.lgcns.codeengine.di.UseCaseEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

interface PluginContext {
    val json: Json
    val useCaseEntryPoint: UseCaseEntryPoint
    val scope: CoroutineScope
    val activity: BaseWebViewActivity
}

class PluginContextImpl(
    override val activity: BaseWebViewActivity,
) : PluginContext {

    override val json = Json {
        ignoreUnknownKeys = true
    }

    override val useCaseEntryPoint by lazy {
        EntryPointAccessors.fromActivity(activity, UseCaseEntryPoint::class.java)
    }

    override val scope: CoroutineScope get() = activity.lifecycleScope

}

