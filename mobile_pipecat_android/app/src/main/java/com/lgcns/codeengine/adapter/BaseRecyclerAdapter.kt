package com.lgcns.codeengine.adapter

import android.content.Context
import androidx.recyclerview.widget.RecyclerView

abstract class BaseRecyclerAdapter<T>(val context: Context?) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var itemList: List<T>? = null

    override fun getItemViewType(position: Int): Int {
        val item = itemList?.get(position)
        return if (item is Pair<*, *>) {
            (item.second as ViewHolderInfo).viewType
        } else {
            Constants.TYPE_ITEM_MAIN
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as? BaseViewHolder<*>)?.onBindViewHolder(getItem(position))
    }

    private fun getItem(position: Int): Any? {
        return itemList?.let {
            it[position]
        }
    }

    override fun getItemCount() = itemList?.size ?: 0

    data class ViewHolderInfo (
        val viewType: Int,
        var isExpend: Boolean = false,
        var isChecked: Boolean = false,
        var nChildCnt: Int = 0,
        val nParentPosition: Int = 0
    )

    object Constants {
        const val TYPE_ITEM_MAIN = 0
        const val TYPE_ITEM_SEARCH = 1
    }
    
}