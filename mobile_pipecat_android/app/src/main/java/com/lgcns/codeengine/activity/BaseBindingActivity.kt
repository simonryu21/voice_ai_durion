package com.lgcns.codeengine.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding

import com.lgcns.codeengine.ui.common.CommonLoadingView

abstract class BaseBindingActivity<T : ViewDataBinding> : AppCompatActivity() {
    abstract var layoutResourceId: Int

    lateinit var binding: T

    private val loadingView: CommonLoadingView by lazy { CommonLoadingView(binding.root) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, layoutResourceId)
    }

    fun showCommonLoading(message: CharSequence = "") {
        loadingView.showLoading(message)
    }

    fun hideCommonLoading() {
        loadingView.hideLoading()
    }

}
