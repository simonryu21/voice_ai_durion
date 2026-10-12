package com.lgcns.codeengine.adapter

import android.content.Context
import android.view.ViewGroup
import com.lgcns.codeengine.R
import com.lgcns.codeengine.data.entity.Place
import com.lgcns.codeengine.databinding.PlaceItemLayoutBinding
import com.lgcns.codeengine.databinding.PlaceSearchItemLayoutBinding
import com.lgcns.codeengine.viewmodel.PlaceListViewModel
import timber.log.Timber

class PlaceListAdapter(context: Context?, private val viewModel: PlaceListViewModel) :
    BaseRecyclerAdapter<Pair<Place, BaseRecyclerAdapter.ViewHolderInfo>>(context) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<Pair<Place, ViewHolderInfo>> {
        return when (viewType) {
            Constants.TYPE_ITEM_MAIN ->
                ItemViewHolder(
                    context = context,
                    parent = parent,
                    viewModel = viewModel
                ) {
                    it?.let { position -> viewModel.onItemViewSelected(position) }
                }
            else -> // TYPE_ITEM_SEARCH
                SearchViewHolder(
                    context = context,
                    parent = parent,
                    viewModel = viewModel
                ) {
                    it?.let { position -> viewModel.onSearchViewSelected(position) }
                }
        }
    }

    class SearchViewHolder(
        context: Context?,
        parent: ViewGroup?,
        val viewModel: PlaceListViewModel, onItemSelected: (Int?) -> Unit
    ) : BaseViewHolder<Pair<Place, ViewHolderInfo>>(context, parent, R.layout.place_search_item_layout, viewModel,
        onItemSelected
    ) {

        override fun onViewCreated(item: Pair<Place, ViewHolderInfo>?) {
            Timber.d("SearchViewHolder onViewCreated")
            getBinding<PlaceSearchItemLayoutBinding>()?.run {
                item?.let { place ->
                    searchPlaceData = place.first
                    Timber.d("search place = $place")
                }
            }
        }
    }

    class ItemViewHolder(
        context: Context?,
        parent: ViewGroup?,
        val viewModel: PlaceListViewModel, onItemSelected: (Int?) -> Unit
    ) : BaseViewHolder<Pair<Place, ViewHolderInfo>>(context, parent, R.layout.place_item_layout, viewModel,
        onItemSelected
    ) {

        override fun onViewCreated(item: Pair<Place, ViewHolderInfo>?) {
            Timber.d("ItemViewHolder onViewCreated")

            getBinding<PlaceItemLayoutBinding>()?.run {
                item?.let { place ->
                    placeData = place.first
                    Timber.d("item place = $place")
                }

                ivDelete.setOnClickListener {
                    viewModel.onItemDeleteClick(bindingAdapterPosition)
                }

                ivFavorite.setOnClickListener {
                    viewModel.onItemFavoriteClick(bindingAdapterPosition)
                }
            }
        }
    }
}