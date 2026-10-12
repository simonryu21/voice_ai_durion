package com.lgcns.codeengine.ui

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.DisplayMetrics
import android.view.*
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.lgcns.codeengine.BuildConfig
import com.lgcns.codeengine.R
import com.lgcns.codeengine.databinding.FragmentWebviewDialogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.net.URL
import java.util.*

class WebViewDialogFragment : DialogFragment() {
    private var _binding: FragmentWebviewDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        _binding = FragmentWebviewDialogBinding.inflate(inflater, container, false)
        initView()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()

        setDialogWindowLayout(1.0, 1.0)
    }

    override fun getTheme(): Int {
        return R.style.CustomWebViewDialogTheme
    }

    /**
     * 화면 크기 계산해서 다이얼로그의 크기를 지정한다.
     */
    private fun setDialogWindowLayout(widthRatio: Double, heightRatio: Double) {
        var width: Int
        var height: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics: WindowMetrics = requireActivity().windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            width = windowMetrics.bounds.width() - insets.left - insets.right
            height = windowMetrics.bounds.height() - insets.top - insets.bottom
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            width = displayMetrics.widthPixels
            height = displayMetrics.heightPixels
        }

        width = (width * widthRatio).toInt()
        height = (height * heightRatio).toInt()

        dialog?.window?.setLayout(width, height)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initView() {
        @Suppress("DEPRECATION") val model = arguments?.getParcelable<WebViewDialogModel>(KEY_DIALOG_MODEL) ?: return

        binding.negativeButton.text = model.negativeBtnText
        binding.positiveButton.text = model.positiveBtnText

        binding.negativeButton.setOnClickListener {
            negativeClickListener?.invoke()
            dismiss()
        }

        binding.positiveButton.setOnClickListener {
            positiveClickListener?.invoke()
            dismiss()
        }

        binding.closeBtn.setOnClickListener {
            dismiss()
        }

        Timber.d("model.showCloseButton=${model.showCloseButton}")
        if (model.showCloseButton) {
            binding.positiveButton.visibility = View.GONE
            binding.negativeButton.visibility = View.GONE
            binding.closeBtn.visibility = View.VISIBLE
        } else {
            binding.positiveButton.visibility = View.VISIBLE
            binding.negativeButton.visibility = View.VISIBLE
            binding.closeBtn.visibility = View.GONE
        }

        model.cancelable?.let {
            isCancelable = it
        }

        binding.webView.apply {
            webViewClient = object : WebViewClient() {

            }
            webChromeClient = WebChromeClient()

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.textZoom = 100
            // TTS/오디오 재생을 위해 사용자 제스처 없이도 미디어 재생 허용
            settings.mediaPlaybackRequiresUserGesture = false

            val url =
                if (model.url.startsWith("http")) model.url else BuildConfig.BASE_URL + model.url
            Timber.d("url = $url")

            // 이미지를 위한 팝업은 zooming을 가능하게 합니다.
            if (checkIfImageUrl(url)) {
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
            }

            loadUrl(url)
        }
    }

    private fun checkIfImageUrl(url: String): Boolean {
        var contentType: String?
        runBlocking(Dispatchers.IO) {
            val connection = URL(url).openConnection()
            contentType = connection.contentType
            Timber.d("contentType=$contentType")
        }

        return contentType?.contains("image/") ?: false
    }


    class Builder {
        private var model = WebViewDialogModel()

        fun setTitle(title: String): Builder {
            model.title = title
            return this
        }

        fun setUrl(url: String): Builder {
            model.url = url
            return this
        }

        fun setPositiveButton(buttonText: String, onClick: (() -> Unit) = {}): Builder {
            model.hasPositiveBtn = true
            model.positiveBtnText = buttonText
            positiveClickListener = onClick
            return this
        }

        fun setNegativeButton(buttonText: String, onClick: (() -> Unit) = {}): Builder {
            model.hasNegativeBtn = true
            model.negativeBtnText = buttonText
            negativeClickListener = onClick
            return this
        }

        fun showCloseButton(isShow: Boolean): Builder {
            model.showCloseButton = isShow
            return this
        }

        fun setCancelable(cancelable: Boolean): Builder {
            model.cancelable = cancelable
            return this
        }

        fun show(manager: FragmentManager): WebViewDialogFragment {
            val dialogFragment = WebViewDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(KEY_DIALOG_MODEL, model)
                }
            }
            dialogFragment.show(manager, TAG)
            return dialogFragment
        }
    }

    @Parcelize
    data class WebViewDialogModel(
        var title: String = "",
        var url: String = "",

        var hasPositiveBtn: Boolean = false,
        var positiveBtnText: String = "",

        var hasNegativeBtn: Boolean = false,
        var negativeBtnText: String = "",

        var showCloseButton: Boolean = false,

        var cancelable: Boolean? = true,
    ) : Parcelable

    companion object {
        private var positiveClickListener: (() -> Unit)? = null
        private var negativeClickListener: (() -> Unit)? = null

        private val TAG = WebViewDialogFragment::class.java.simpleName
        const val KEY_DIALOG_MODEL = "KEY_DIALOG_MODEL"
    }
}
