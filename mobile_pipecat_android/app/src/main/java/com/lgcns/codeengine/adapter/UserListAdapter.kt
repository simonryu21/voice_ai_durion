package com.lgcns.codeengine.adapter

import android.content.Context
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.lgcns.codeengine.R
import com.lgcns.codeengine.data.entity.User
import com.lgcns.codeengine.databinding.UserItemLayoutBinding
import com.lgcns.codeengine.viewmodel.UserListViewModel

class UserListAdapter(context: Context?, private val viewModel: UserListViewModel) :
    BaseRecyclerAdapter<User>(context) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(context = context,
            parent = parent,
            viewModel = viewModel)  {
            it?.let { position -> viewModel.onItemViewSelected(position) }
        }
    }

    class ViewHolder(
        context: Context?,
        parent: ViewGroup?,
        val viewModel: UserListViewModel, onItemSelected: (Int?) -> Unit
    ) : BaseViewHolder<User>(context, parent, R.layout.user_item_layout, viewModel, onItemSelected) {

        override fun onViewCreated(item: User?) {
            getBinding<UserItemLayoutBinding>()?.run {
                item?.let { user ->
                    userData = user

                    Glide.with(imageViewAvatar.context)
                        .load(user.avatar)
                        .into(imageViewAvatar)
                }
            }
        }
    }
}