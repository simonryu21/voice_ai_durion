package com.lgcns.codeengine.ui.common

import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.postDelayed

class SoftKeyboardVisibilityHelper(
    private val targetView: View,
) {

    fun setVisibility(visible: Boolean) {
        if (visible) show() else hide()
    }

    private fun show() {
        val manager = targetView.context.getSystemService(InputMethodManager::class.java)
        if (!targetView.isFocused) {
            targetView.requestFocus()
        }
        targetView.postDelayed(50) {
            manager.showSoftInput(targetView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hide() {
        val manager = targetView.context.getSystemService(InputMethodManager::class.java)
        manager.hideSoftInputFromWindow(targetView.windowToken, 0)
    }
}