package com.lgcns.codeengine.activity

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.lgcns.codeengine.BuildConfig
import com.lgcns.codeengine.R
import com.lgcns.codeengine.activity.BaseWebViewActivity.Companion.LOAD_URL
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ErrorShowActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        when (intent?.getIntExtra(ERROR_CODE, 500) ?: 500) {
            -2 -> {
                setContentView(R.layout.activity_2_error)

                findViewById<TextView>(R.id.tvExit).setOnClickListener {
                    finishAffinity()
                }

                findViewById<TextView>(R.id.btn_box).setOnClickListener {
                    if (isNetworkAvailable(this)) {
                        val loadUrl = intent?.getStringExtra(LOAD_URL) ?: BuildConfig.BASE_URL
                        val intent = Intent(this, WebViewActivity::class.java)
                        intent.putExtra(LOAD_URL, loadUrl)
                        startActivity(intent)
                        finish()
                    }
                }
            }
            404 -> {
                setContentView(R.layout.activity_404_error)

                findViewById<TextView>(R.id.tvHome).setOnClickListener {
                    val intent = Intent(this, WebViewActivity::class.java)
                    intent.putExtra(LOAD_URL, BuildConfig.BASE_URL)
                    startActivity(intent)
                    finish()
                }
            }
            else -> { // in 500..599 -> {
                setContentView(R.layout.activity_500_error)

                findViewById<TextView>(R.id.tvPrevious).setOnClickListener {
                    finish()
                }

                findViewById<TextView>(R.id.btn_box).setOnClickListener {
                    val loadUrl = intent?.getStringExtra(LOAD_URL) ?: BuildConfig.BASE_URL
                    val intent = Intent(this, WebViewActivity::class.java)
                    intent.putExtra(LOAD_URL, loadUrl)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {

        // register activity with the connectivity manager service
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // if the android version is equal to M
        // or greater we need to use the
        // NetworkCapabilities to check what type of
        // network has the internet connection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            // Returns a Network object corresponding to
            // the currently active default data network.
            val network = connectivityManager.activeNetwork ?: return false

            // Representation of the capabilities of an active network.
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                // Indicates this network uses a Wi-Fi transport,
                // or WiFi has network connectivity
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true

                // Indicates this network uses a Cellular transport. or
                // Cellular has network connectivity
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true

                // else return false
                else -> false
            }
        } else {
            // if the android version is below M
            @Suppress("DEPRECATION") val networkInfo =
                connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    override fun onStart() {
        super.onStart()
        isActive = true
    }

    override fun onStop() {
        super.onStop()
        isActive = false
    }

    companion object {
        const val ERROR_CODE = "ERROR_CODE"

        var isActive = false
    }
}