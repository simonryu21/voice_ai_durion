package com.lgcns.codeengine.ui.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CustomBottomSheetDialogModel(
    var title: String = "",
    var message: String = "",
    var highlightedMessage: String = "",

    var hasCloseBtn: Boolean = false,

    var hasPositiveBtn: Boolean = false,
    var positiveBtnText: String = "",

    var hasNegativeBtn: Boolean = false,
    var negativeBtnText: String = "",

    var cancelable: Boolean = true,
) : Parcelable
