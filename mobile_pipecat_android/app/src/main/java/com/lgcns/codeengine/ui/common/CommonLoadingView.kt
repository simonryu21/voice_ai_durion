package com.lgcns.codeengine.ui.common

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.lgcns.codeengine.R
import com.lgcns.codeengine.databinding.LayoutLoadingViewBinding
import com.linecorp.apng.ApngDrawable
import com.linecorp.apng.ApngDrawable.Companion.LOOP_FOREVER
import com.linecorp.apng.decoder.ApngException

class CommonLoadingView(parentView: View) {

    private val binding: LayoutLoadingViewBinding =
        LayoutLoadingViewBinding.inflate(LayoutInflater.from(parentView.context))

    private var loadingDrawable: ApngDrawable? = null

    init {
        initView(parentView)
    }

    @SuppressLint("Range")
    private fun initView(parentView: View) {
        val isApng = parentView.context.assets.open(LOADING_IMAGE).buffered().use {
            ApngDrawable.isApng(it)
        }

        if (isApng) {
            try {
                loadingDrawable = ApngDrawable.decode(parentView.context.assets, LOADING_IMAGE)
            } catch (e: ApngException) {
                return
            }
        }

        binding.loadingImageView.setImageDrawable(loadingDrawable)
        loadingDrawable?.loopCount = LOOP_FOREVER

        binding.root.layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        (parentView as ViewGroup).addView(binding.root)
    }

    fun showLoading(message: CharSequence = "") {
        binding.loadingLayout.visibility = View.VISIBLE
        binding.loadingTextView.text = message
        binding.loadingTextView.isVisible = message.isNotBlank()
        if (message.isNotEmpty()) {
            setBackgroundDim()
        }
        loadingDrawable?.start()
    }

    fun hideLoading() {
        loadingDrawable?.stop()
        binding.loadingLayout.visibility = View.GONE
        eliminateBackgroundDim()
    }

    private fun setBackgroundDim() {
        binding.loadingLayout.setBackgroundColor(
            ContextCompat.getColor(
                binding.root.context,
                R.color.white_80
            )
        )
    }

    private fun eliminateBackgroundDim() {
        binding.loadingLayout.setBackgroundColor(
            ContextCompat.getColor(
                binding.root.context,
                R.color.transparent
            )
        )
    }

    companion object {
        private const val LOADING_IMAGE = "main_spinner.png"
    }
}