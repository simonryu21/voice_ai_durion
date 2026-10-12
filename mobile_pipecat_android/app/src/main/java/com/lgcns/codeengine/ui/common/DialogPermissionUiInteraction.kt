package com.lgcns.codeengine.ui.common

import android.content.Context
import androidx.fragment.app.FragmentManager
import com.lgcns.codeengine.R
import com.lgcns.codeengine.web.permission.FeaturePermissionUiInteraction

class DialogPermissionUiInteraction(
    private val context: Context,
    private val fragmentManager: FragmentManager,
) : FeaturePermissionUiInteraction {

    override fun confirmRequestPermissionRationale(
        message: CharSequence,
        onConfirmed: (Boolean) -> Unit
    ) {
        CustomBottomSheetDialogFragment.Builder()
            .setTitle(context.getString(R.string.feature_permission_guide_title))
            .setMessage(message.toString())
            .setPositiveButton(context.getString(R.string.feature_permission_ok_label)) {
                onConfirmed(true)
            }
            .setNegativeButton(context.getString(R.string.feature_permission_cancel_label)) {
                onConfirmed(false)
            }
            .setCancelable(false)
            .show(fragmentManager)
    }

    override fun confirmGoAppDetailsSettings(
        message: CharSequence,
        onConfirmed: (Boolean) -> Unit
    ) {
        CustomBottomSheetDialogFragment.Builder()
            .setTitle(context.getString(R.string.feature_permission_guide_title))
            .setMessage(message.toString())
            .setPositiveButton(context.getString(R.string.feature_permission_ok_label)) {
                onConfirmed(true)
            }
            .setNegativeButton(context.getString(R.string.feature_permission_cancel_label)) {
                onConfirmed(false)
            }
            .setCancelable(false)
            .show(fragmentManager)
    }
}