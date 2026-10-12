package com.lgcns.codeengine.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.lgcns.codeengine.databinding.LayoutCustomSnackbarBinding

class CustomSnackBar(view: View, message: String) {

    private val binding: LayoutCustomSnackbarBinding =
        LayoutCustomSnackbarBinding.inflate(LayoutInflater.from(view.context))

    private val snackBar = Snackbar.make(view, "", Snackbar.LENGTH_SHORT)

    init {
        initView()
        setMessage(message)
    }

    private fun initView() = with(snackBar.view as Snackbar.SnackbarLayout) {
        removeAllViews()
        setPadding(20.dp, 0, 20.dp, 0)
        setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
        addView(binding.root)
    }

    private fun setMessage(message: String) {
        binding.message.text = message
    }

    /**
     * 스낵바를 배치할 기준뷰 설정.
     * 설정한 View 위로 스낵바가 보여진다.
     */
    fun setAnchorView(view: View): CustomSnackBar {
        snackBar.anchorView = view
        return this
    }

    /**
     * 메시지 Gravity 설정 (default : 중앙정렬)
     * 디자인 가이드 -> 한 줄일 경우(중앙 정렬), 두 줄 이상일 경우(왼쪽 정렬)
     */
    fun setGravity(gravity: Int): CustomSnackBar {
        binding.message.gravity = gravity
        return this
    }

    /**
     * 스낵바 Bottom 마진 값 설정.
     * AnchorView를 지정하기 애매한 케이스의 경우 직접 마진 값을 설정해준다.
     */
    fun setBottomMargin(margin: Int): CustomSnackBar {
        val view = snackBar.view
        val params = view.layoutParams as ViewGroup.MarginLayoutParams
        params.setMargins(0, 0, 0, margin.dp)
        view.layoutParams = params
        return this
    }

    /**
     * 스낵바 표출 시간 설정 (default : Snackbar.LENGTH_SHORT)
     */
    fun setDuration(duration: Int): CustomSnackBar {
        snackBar.duration = duration
        return this
    }

    /**
     * 스낵바 내부 ProgressBar Visibility 설정. (기본 값 : gone)
     */
    fun setProgressBar(isShow: Boolean): CustomSnackBar {
        val visibility = if (isShow) View.VISIBLE else View.GONE
        binding.progressBar.visibility = visibility
        return this
    }

    /**
     * 스낵바 dismissEvent 설정
     */
    fun setDismissedEvent(onDismissEvent: () -> Unit): CustomSnackBar {
        snackBar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                super.onDismissed(transientBottomBar, event)
                onDismissEvent.invoke()
            }
        })
        return this
    }

    fun show() {
        snackBar.show()
    }

    fun dismiss() {
        snackBar.dismiss()
    }

    companion object {
        fun make(view: View, message: String) = CustomSnackBar(view, message)
        fun make(view: View, @StringRes resId: Int): CustomSnackBar {
            val message = view.context.resources.getString(resId)
            return CustomSnackBar(view, message)
        }

        // BottomNavigation이 있는 화면에서 사용할 기본 마진 값
        const val MARGIN_BOTTOM_OVER_BOTTOM_NAVIGATION = 60
    }
}