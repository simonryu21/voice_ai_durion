package com.lgcns.codeengine.ui.dev

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.util.PatternsCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.lgcns.codeengine.BuildConfig
import com.lgcns.codeengine.R
import com.lgcns.codeengine.activity.BaseWebViewActivity.Companion.EXTRA_DATA
import com.lgcns.codeengine.activity.BaseWebViewActivity.Companion.LOAD_URL
import com.lgcns.codeengine.activity.WebViewActivity
import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository
import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository.Companion.REACT_TEST_URL
import com.lgcns.codeengine.databinding.FragmentWebTestDialogBinding
import com.lgcns.codeengine.util.isLocalDevUrl
import com.lgcns.codeengine.ui.common.dp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class WebTestDialogFragment : DialogFragment() {

    private var _binding: FragmentWebTestDialogBinding? = null
    val binding get() = _binding!!

    @Inject
    lateinit var dataStoreRepository: DataStoreRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_WebViewTest)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebTestDialogBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
        }

        lifecycleScope.launch {
            val url = dataStoreRepository.getDataStoreStringOnce(REACT_TEST_URL) ?: DEFAULT_URL_REACT
            binding.reactUrlEdit.setText(url)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        activity?.finish()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setClicks()

        binding.root.visibility = View.GONE
        binding.root.post {
            val screenMaxWidth = resources.getDimensionPixelOffset(R.dimen.screen_max_width)
            if (binding.root.rootView.width > screenMaxWidth) {
                val sideMargin = ((binding.root.rootView.width - screenMaxWidth) / 2) + 20.dp
                val marginLayoutParams = binding.root.layoutParams as ViewGroup.MarginLayoutParams
                marginLayoutParams.marginStart = sideMargin
                marginLayoutParams.marginEnd = sideMargin

                binding.root.layoutParams = marginLayoutParams
            }

            binding.root.visibility = View.VISIBLE
        }
    }

    private fun setClicks() = with(binding) {
        webViewReactButton.setOnClickListener {
            val url = reactUrlEdit.getValidUrl() ?: return@setOnClickListener
            moveWithUrl(url)
            activity?.finish()
        }

        pluginButton.setOnClickListener {
            moveWithUrl(BuildConfig.WEBVIEW_PLUGIN_TEST)
        }

        closeBtn.setOnClickListener {
            activity?.finish()
        }
    }

    private fun moveWithUrl(url: String? = null, params: JSONObject? = null) {
        Timber.d("moveWithUrl url : $url / params : $params")

        startActivity(Intent(requireContext(), WebViewActivity::class.java).apply {
            url?.let { putExtra(LOAD_URL, url) }
            params?.let { putExtra(EXTRA_DATA, it.toString()) }
        })

        // save to data store
        lifecycleScope.launch {
            if (url != null && url != BuildConfig.WEBVIEW_PLUGIN_TEST) dataStoreRepository.setDataStoreString(REACT_TEST_URL, url)
        }
    }

    private fun EditText.getValidUrl(): String? {
        val input = text.toString().trim()
        if (input.isEmpty()) return null
        // PatternsCompat.WEB_URL은 TLD가 없는 localhost를 URL로 인정하지 않으므로,
        // 로컬 개발용 loopback/에뮬레이터 호스트(localhost, 127.0.0.1, 10.0.2.2 등)는 별도로 허용한다.
        return input.takeIf { isLocalDevUrl(it) || PatternsCompat.WEB_URL.matcher(it).matches() }
    }

    companion object {
        fun show(manager: FragmentManager) {
            WebTestDialogFragment().show(manager, WebTestDialogFragment::class.simpleName)
        }

        private const val DEFAULT_URL_REACT = "https://appdept.shinsegae.com/"
    }
}
