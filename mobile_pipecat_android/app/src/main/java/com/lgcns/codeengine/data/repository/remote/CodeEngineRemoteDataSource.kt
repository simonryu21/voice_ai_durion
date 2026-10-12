package com.lgcns.codeengine.data.repository.remote

import android.annotation.SuppressLint
import com.google.gson.GsonBuilder
import com.lgcns.codeengine.BuildConfig
import com.lgcns.codeengine.CodeEngineApplication
import com.lgcns.codeengine.data.entity.Auth
import com.lgcns.codeengine.data.entity.User
import com.lgcns.codeengine.data.repository.remote.model.LoginResponse
import com.lgcns.codeengine.data.repository.remote.model.UserInfoResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.security.KeyManagementException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Retrofit API declaration for CodeEngine Network API
 */
interface CodeEngineNetworkApi {

    //////////////////////////////////////////////////////////////////////////////////
    // Remote GET/POST API 추가
    
    @POST("auth/login")
    suspend fun login(
        @Body auth: Auth
    ): LoginResponse

    @GET("auth/refresh")
    suspend fun refreshToken(
        @Header("Authorization") token: String,
    ): Response<LoginResponse>

    @GET("user/info")
    suspend fun getUserInfo(): UserInfoResponse

    @GET("users")
    suspend fun getUsers(): List<User>


    //////////////////////////////////////////////////////////////////////////////////
}

/**
 * [Retrofit] backed [CodeEngineRemoteDataSource]
 */
class CodeEngineRemoteDataSource internal constructor (
    authInterceptor: AuthInterceptor,
    authAuthenticator: AuthAuthenticator,
) {

    private var sslSocketFactory: SSLSocketFactory? = null
    private var trustManager: TrustManager? = null
    private var gson = GsonBuilder().setLenient().create()

    //////////////////////////////////////////////////////////////////////////////////
    // Remote API 추가 (Remote GET/POST API와 1:1 대응)
    suspend fun login(auth: Auth) = networkApi.login(auth)

    suspend fun getUserInfo() = networkAuthApi.getUserInfo()

    suspend fun getUsers() = remoteTestApi.getUsers()


    //////////////////////////////////////////////////////////////////////////////////

    // JWT 인증이 필요없는 API일 때 사용 (http://)
    private val networkApi = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(
                    // Note: You can change the logging level
                    HttpLoggingInterceptor().apply {
                        setLevel(HttpLoggingInterceptor.Level.BODY)
                    },
                )
                .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                .build(),
        )
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(CodeEngineNetworkApi::class.java)

    // JWT 인증이 필요한 API일 때 사용 (https://)
    private val networkAuthApi = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(
            OkHttpClient.Builder()
                //@todo 실제 사용할때는 "res/raw"밑에 crt 파일을 복사한 후, 아래 comment를 풀고 사용합니다.
                /*
                .sslSocketFactory(
                    getPinnedCertSslSocketFactory()!!,
                    trustManager as X509TrustManager
                )
                 */
                .addInterceptor(authInterceptor)
                .addInterceptor(
                    // Note: You can change the logging level
                    HttpLoggingInterceptor().apply {
                        setLevel(HttpLoggingInterceptor.Level.BODY)
                    },
                )
                .authenticator(authAuthenticator)
                .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                .build(),
        )
        .addConverterFactory(GsonConverterFactory.create(gson))/* Converter Factory to convert your Json to gson */
        .build()
        .create(CodeEngineNetworkApi::class.java)

    //@todo RemoteDataSource를 테스트하기 위한 변수이므로 삭제 가능합니다.
    private val remoteTestApi = Retrofit.Builder()
        .baseUrl(BuildConfig.REMOTE_TEST_URL)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(
                    // Note: You can change the logging level
                    HttpLoggingInterceptor().apply {
                        setLevel(HttpLoggingInterceptor.Level.BODY)
                    },
                )
                .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                .build(),
        )
        .addConverterFactory(
            GsonConverterFactory.create()
        )
        .build()
        .create(CodeEngineNetworkApi::class.java)

    /**
     * ssl.cer 인증서 정보로 ssl 연결을 시도합니다.
     * @return SSLSocketFactory
     */
    @SuppressLint("DiscouragedApi")
    fun getPinnedCertSslSocketFactory(): SSLSocketFactory? {
        if (sslSocketFactory != null) return sslSocketFactory

        try {
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val crtName: String = URL(BuildConfig.BASE_URL).host.replace(
                '.',
                '_'
            ) // "https://", port는 생략한 후에 '.'을 '_'으로 변경
            Timber.d("crtName=$crtName")

            val caInput: InputStream =
                CodeEngineApplication.getGlobalApplicationContext()!!.resources.openRawResource(
                    CodeEngineApplication.getGlobalApplicationContext()!!.resources.getIdentifier(
                        crtName,
                        "raw", CodeEngineApplication.getGlobalApplicationContext()!!.packageName
                    )
                )

            var certificate: Certificate? = null
            try {
                certificate = certificateFactory.generateCertificate(caInput)
            } catch (e: CertificateException) {
                e.printStackTrace()
            } finally {
                caInput.close()
            }

            val keyStoreType = KeyStore.getDefaultType()
            val keyStore = KeyStore.getInstance(keyStoreType)
            keyStore.load(null, null)
            if (keyStore == null) {
                return null
            }

            keyStore.setCertificateEntry("ca", certificate)
            val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
            val trustManagerFactory = TrustManagerFactory.getInstance(tmfAlgorithm)
            trustManagerFactory.init(keyStore)
            trustManager = trustManagerFactory.trustManagers[0]
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustManagerFactory.trustManagers, null)
            sslSocketFactory = sslContext.socketFactory

            return sslSocketFactory
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: KeyStoreException) {
            e.printStackTrace()
        } catch (e: KeyManagementException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    companion object {
        private const val CONNECTION_TIMEOUT = 10_000L
    }

}
