package com.lgcns.codeengine.util

import android.widget.ImageView
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.lgcns.codeengine.R

object ImageBindingAdapter {
    @BindingAdapter(value = ["imageUrl", "isCircle"], requireAll = false)
    @JvmStatic
    fun ImageView.bindImageUrl(imageUrl: String?, isCircle: Boolean) {
        if (imageUrl.isNullOrEmpty()) {
            if (isCircle) {
                Glide.with(this.context)
                    .load(R.drawable.ic_profile_empty)
                    .circleCrop()
                    .into(this)
            } else {
                Glide.with(this.context)
                    .load(R.drawable.ic_profile_empty)
                    .into(this)
            }
        } else {
            if (isCircle) {
                Glide.with(this.context)
                    .load(imageUrl)
                    .error(R.drawable.ic_profile_empty)
                    .circleCrop()
                    .into(this)
            } else {
                Glide.with(this.context)
                    .load(imageUrl)
                    .error(R.drawable.ic_profile_empty)
                    .into(this)
            }
        }
    }
}