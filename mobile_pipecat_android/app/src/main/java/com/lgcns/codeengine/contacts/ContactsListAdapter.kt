package com.lgcns.codeengine.contacts

import android.content.Context
import android.view.ViewGroup
import com.lgcns.codeengine.R
import com.lgcns.codeengine.adapter.BaseRecyclerAdapter
import com.lgcns.codeengine.adapter.BaseViewHolder
import com.lgcns.codeengine.databinding.ContactItemLayoutBinding
import timber.log.Timber

class ContactsListAdapter(context: Context?, private val viewModel: ContactsViewModel) :
    BaseRecyclerAdapter<ContactData>(context) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseViewHolder<ContactData> {
        return ItemViewHolder(
            context = context,
            parent = parent,
            viewModel = viewModel
        ) {
            it?.let { position -> viewModel.onItemViewSelected(position) }
        }
    }

    class ItemViewHolder(
        context: Context?,
        parent: ViewGroup?,
        val viewModel: ContactsViewModel, onItemSelected: (Int?) -> Unit
    ) : BaseViewHolder<ContactData>(
        context, parent, R.layout.contact_item_layout, viewModel,
        onItemSelected
    ) {

        override fun onViewCreated(item: ContactData?) {
            Timber.d("ItemViewHolder onViewCreated")

            getBinding<ContactItemLayoutBinding>()?.run {
                item?.let {
                    contactData = item
                    Timber.d("item contact = $item")
                }
            }
        }
    }
}