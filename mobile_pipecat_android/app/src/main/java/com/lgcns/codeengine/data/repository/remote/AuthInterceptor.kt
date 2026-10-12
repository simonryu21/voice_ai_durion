package com.lgcns.codeengine.data.repository.remote

import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository
import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository.Companion.TOKEN_KEY
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor() : Interceptor {

    @Inject
    lateinit var dataStoreRepository: DataStoreRepository

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking {
            dataStoreRepository.getDataStoreString(TOKEN_KEY).first()
        }
        val request = chain.request().newBuilder()
        request.addHeader("Authorization", "Bearer $token")
        return chain.proceed(request.build())
    }
}