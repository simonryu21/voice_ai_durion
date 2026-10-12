package com.lgcns.codeengine.di

import com.lgcns.codeengine.web.usecase.WebViewPluginUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

/**
 * WebView Plugin을 통해서 요청이 들어오는 경우 의존성 추출(Dependency Injection)을 위해 아래
 * UseCaseEntryPoint를 사용합니다.
 */
@EntryPoint
@InstallIn(ActivityComponent::class)
interface UseCaseEntryPoint {

    fun provideWebViewPluginUseCase(): WebViewPluginUseCase

}
