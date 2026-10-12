package com.lgcns.codeengine.ui.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CustomDialogModel(
    var title: String = "",
    var message: String = "",

    var hasPositiveBtn: Boolean = false,
    var positiveBtnText: String = "",

    var hasNegativeBtn: Boolean = false,
    var negativeBtnText: String = "",

    var cancelable: Boolean? = null,
) : Parcelable
