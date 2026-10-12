package com.lgcns.codeengine.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lgcns.codeengine.adapter.BaseRecyclerAdapter
import com.lgcns.codeengine.data.Result
import com.lgcns.codeengine.data.entity.Place
import com.lgcns.codeengine.data.repository.CodeEngineRepository
import com.lgcns.codeengine.util.eventflow.MutableEventFlow
import com.lgcns.codeengine.util.eventflow.asEventFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.stream.Collectors
import javax.inject.Inject

@HiltViewModel
class PlaceListViewModel @Inject constructor() : BaseViewModel() {

    enum class SelectedTab {
        RECENT, FAVORITE
    }

    @Inject
    lateinit var repository: CodeEngineRepository

    lateinit var searchedPlaceList: List<Pair<Place, BaseRecyclerAdapter.ViewHolderInfo>>

    val recentPlaces = MutableLiveData<List<Pair<Place, BaseRecyclerAdapter.ViewHolderInfo>>?>(ArrayList())

    private val _itemSelected = MutableEventFlow<Any>()
    val itemSelected = _itemSelected.asEventFlow()

    var selectedTab = SelectedTab.RECENT

    fun observeRecentPlaces() {
        viewModelScope.launch {
            repository.getPlacesStream().collect {
                when (it) {
                    is Result.Success -> {
                        Timber.d("The place stream was collected.")

                        val places = ArrayList<Pair<Place, BaseRecyclerAdapter.ViewHolderInfo>>(ArrayList())

                        it.data.forEach {
                                item ->
                            places.add(
                                Pair(item,
                                    BaseRecyclerAdapter.ViewHolderInfo(BaseRecyclerAdapter.Constants.TYPE_ITEM_MAIN)
                                )
                            )
                        }
                        recentPlaces.value = places
                    }
                    is Result.Error -> {
                        Timber.e(it.exception)
                    }
                }
            }
        }
    }

    fun onSearchViewSelected(position: Int) {
        Timber.d("onSearchViewSelected() selected place: ${searchedPlaceList[position]}")

        viewModelScope.launch {

            _itemSelected.emit(1)

            if (recentPlaces.value?.stream()?.filter {
                        place -> place.first == searchedPlaceList[position].first
                }?.findFirst()?.isPresent == true)
            {
                Timber.d("already inserted.")
                return@launch
            }

            repository.insertPlace(searchedPlaceList[position].first)
        }
    }

    fun onItemViewSelected(position: Int) {
        Timber.d("onItemViewSelected() selected place: ${getSelectedItem(position)}")

        viewModelScope.launch {
            _itemSelected.emit(1)
        }
    }

    fun onItemDeleteClick(position: Int) {
        viewModelScope.launch {
            repository.deletePlace(getSelectedItem(position)?.id ?: -1)
        }
    }

    fun onItemFavoriteClick(position: Int) {
        viewModelScope.launch {
            getSelectedItem(position)?.let { place ->
                repository.favoritePlace(place, !place.isFavorite)
            }
        }
    }

    private fun getSelectedItem(position: Int) : Place? {
        return when (selectedTab) {
            SelectedTab.RECENT -> recentPlaces.value?.get(position)?.first
            SelectedTab.FAVORITE -> recentPlaces.value?.stream()
                ?.filter { place -> place.first.isFavorite }
                ?.collect(Collectors.toList())?.get(position)?.first
        }
    }
}