package com.lgcns.codeengine.data.repository.remote

import com.lgcns.codeengine.BuildConfig
import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository
import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository.Companion.TOKEN_KEY
import com.lgcns.codeengine.data.repository.remote.model.LoginResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject


class AuthAuthenticator @Inject constructor() : Authenticator {

    @Inject
    lateinit var dataStoreRepository: DataStoreRepository

    override fun authenticate(route: Route?, response: Response): Request? {
        val token = runBlocking {
            dataStoreRepository.getDataStoreString(TOKEN_KEY).first()
        }

        return runBlocking {
            val newToken = getNewToken(token)

            if (!newToken.isSuccessful || newToken.body() == null) { //Couldn't refresh the token, so restart the login process
                dataStoreRepository.removeDataStoreString(TOKEN_KEY)
            }

            newToken.body()?.let {
                dataStoreRepository.setDataStoreString(TOKEN_KEY, it.token)
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${it.token}")
                    .build()
            }
        }
    }

    private suspend fun getNewToken(refreshToken: String?): retrofit2.Response<LoginResponse> {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
        val okHttpClient = OkHttpClient.Builder().addInterceptor(loggingInterceptor).build()

        val baseUrl = BuildConfig.BASE_URL
        /*
        val baseUrl = if (BuildConfig.DEBUG) {
            dataStoreRepository.getDataStoreStringOnce(DataStoreRepository.REACT_TEST_URL)
                ?: BuildConfig.BASE_URL
        } else {
            BuildConfig.BASE_URL
        }
         */

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
        val service = retrofit.create(CodeEngineNetworkApi::class.java)
        return service.refreshToken("Bearer $refreshToken")
    }

}
