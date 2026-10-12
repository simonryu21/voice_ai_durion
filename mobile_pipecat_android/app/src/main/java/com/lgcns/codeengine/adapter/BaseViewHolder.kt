package com.lgcns.codeengine.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.lgcns.codeengine.viewmodel.BaseViewModel

abstract class BaseViewHolder<in ITEM : Any>(
    context: Context?,
    parent: ViewGroup?,
    @LayoutRes layoutRes: Int,
    viewModel: BaseViewModel,
    onItemSelected: (Int?) -> Unit,
) : RecyclerView.ViewHolder(LayoutInflater.from(context).inflate(layoutRes, parent, false)) {
    init {
        itemView.setOnClickListener {
            onItemSelected.invoke(bindingAdapterPosition)
        }
    }

    fun <ViewDataBinding : androidx.databinding.ViewDataBinding?> getBinding(): ViewDataBinding? {
        return DataBindingUtil.bind<ViewDataBinding>(itemView)
    }

    @Suppress("UNCHECKED_CAST")
    fun onBindViewHolder(item: Any?) {
        try {
            onViewCreated(item as? ITEM?)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    abstract fun onViewCreated(item: ITEM?)
}