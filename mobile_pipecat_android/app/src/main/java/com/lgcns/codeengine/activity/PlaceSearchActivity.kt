package com.lgcns.codeengine.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.lgcns.codeengine.BuildConfig
import com.lgcns.codeengine.R
import com.lgcns.codeengine.adapter.BaseRecyclerAdapter
import com.lgcns.codeengine.adapter.PlaceListAdapter
import com.lgcns.codeengine.data.entity.Place
import com.lgcns.codeengine.databinding.ActivityPlaceSearchBinding
import com.lgcns.codeengine.ui.CustomDialogFragment
import com.lgcns.codeengine.util.SecurityUtils
import com.lgcns.codeengine.util.extension.collectRepeatOnStarted
import com.lgcns.codeengine.viewmodel.PlaceListViewModel
import com.skt.Tmap.TMapData
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.stream.Collectors

/**
 * LocalDataSource를 테스트할 수 있는 Activity입니다.
 * Hilt DI, Coroutine, Flow 등을 사용했습니다.
 * 목적지를 입력하면 TMap API를 통해 목적지 리스트들을 가지고 옵니다.
 * 검색된 목적지 리스트 중 한 개를 터치하면 자동으로 최근 검색 리스트에 저장되고,
 * RemoteDataSource를 테스트할 수 있는 UserListActivity로 이동합니다.
 * 최근 검색 리스트에서는 삭제 및 즐겨찾기 추가가 가능합니다.
 * 즐겨찾기로 추가된 아이템들은 "장소 즐겨찾기" 탭으로 이동하면 보입니다.
 */
@AndroidEntryPoint
class PlaceSearchActivity : AppCompatActivity() {

    private lateinit var mainBinding: ActivityPlaceSearchBinding

    private val viewModel: PlaceListViewModel by lazy {
        ViewModelProvider(this)[PlaceListViewModel::class.java]
    }

    private val adapter: PlaceListAdapter by lazy {
        PlaceListAdapter(this, viewModel)
    }

    private val mutex = Mutex()

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        Timber.d("Start PlaceSearchActivity..")

        mainBinding = DataBindingUtil.setContentView(this, R.layout.activity_place_search)

        val rooted = SecurityUtils.checkRooting(this)
        Timber.d("[Rooting Check] Is it rooted? $rooted")

        if (rooted && !BuildConfig.DEBUG) {
            CustomDialogFragment.Builder()
                .setMessage(getString(R.string.str_rootingcheck_msg) ?: "")
                .setPositiveButton(getString(R.string.ok_button)) { finishAffinity() }
                .setCancelable(false)
                .show(supportFragmentManager)

            return
        }

        setView()
        setObserver()

        viewModel.observeRecentPlaces()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setObserver() {
        viewModel.itemSelected.collectRepeatOnStarted(this) { event ->
            event.let {
                val intent = Intent(this, UserListActivity::class.java)
                startActivity(intent)
                //finish() <-- back key 누르면 현재 activity로 돌아오게 하기 위해
            }
        }

        viewModel.recentPlaces.observe(this) {
            mainBinding.recycleSearchResult.recycledViewPool.clear()

            when (viewModel.selectedTab ) {
                PlaceListViewModel.SelectedTab.RECENT ->
                    adapter.itemList = it
                PlaceListViewModel.SelectedTab.FAVORITE ->
                    adapter.itemList = viewModel.recentPlaces.value?.stream()?.filter { place -> place.first.isFavorite}
                    ?.collect(Collectors.toList())
            }

            adapter.notifyDataSetChanged()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setView() {
        mainBinding.recycleSearchResult.adapter = adapter

        mainBinding.etSearch.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            @SuppressLint("NotifyDataSetChanged")
            override fun afterTextChanged(s: Editable) {
                Timber.d("afterTextChanged() : s= $s")

                if (s.isEmpty()) {
                    mainBinding.llQuickFavoriteRoute.visibility = View.VISIBLE
                    mainBinding.vDivider.visibility = View.VISIBLE

                    mainBinding.tvPlaceFav.setBackgroundResource(R.drawable.transparent_background)
                    mainBinding.tvRecentSearch.setBackgroundResource(R.drawable.horizontal_twilight_line)

                    mainBinding.recycleSearchResult.recycledViewPool.clear()
                    adapter.itemList = viewModel.recentPlaces.value
                    adapter.notifyDataSetChanged()
                    return
                }

                mainBinding.llQuickFavoriteRoute.visibility = View.GONE
                mainBinding.vDivider.visibility = View.GONE

                TMapData().findAllPOI(s.toString(), 10) {
                    if (it == null) {
                        Timber.e("it is null.")
                        return@findAllPOI
                    }

                    // UI에 영향을 미치는 작업이므로 Dispatchers.Main을 사용합니다.
                    // 그리고, RecyclerAdapter list를 change하는 사이에 TMap에서 response가 와서
                    // RecyclerAdapter에서 exception이 발생하는 경우를 막기 위해 mutex lock을 사용합니다.
                    lifecycleScope.launch(Dispatchers.Main) {
                        mutex.withLock {
                            val places = ArrayList<Pair<Place, BaseRecyclerAdapter.ViewHolderInfo>>(ArrayList())
                            for (item in it) {
                                Timber.d("POI Name: ${item.poiName}, Address: ${item.poiAddress}, Point: ${item.poiPoint}")
                                places.add(Pair(
                                    Place(item.poiName, item.poiAddress.replace("null", ""), item.poiPoint.longitude, item.poiPoint.latitude),
                                    BaseRecyclerAdapter.ViewHolderInfo(BaseRecyclerAdapter.Constants.TYPE_ITEM_SEARCH)
                                ))
                            }

                            mainBinding.recycleSearchResult.recycledViewPool.clear()

                            adapter.itemList = places
                            viewModel.searchedPlaceList = places

                            adapter.notifyDataSetChanged()
                        }
                    }
                }
            }
        })

        mainBinding.ivClose.setOnClickListener {
            finish()
        }

        mainBinding.tvRecentSearch.setOnClickListener {
            mainBinding.tvPlaceFav.setBackgroundResource(R.drawable.transparent_background)
            mainBinding.tvRecentSearch.setBackgroundResource(R.drawable.horizontal_twilight_line)

            viewModel.selectedTab = PlaceListViewModel.SelectedTab.RECENT
            mainBinding.recycleSearchResult.recycledViewPool.clear()
            adapter.itemList = viewModel.recentPlaces.value
            adapter.notifyDataSetChanged()
        }

        mainBinding.tvPlaceFav.setOnClickListener {
            mainBinding.tvPlaceFav.setBackgroundResource(R.drawable.horizontal_twilight_line)
            mainBinding.tvRecentSearch.setBackgroundResource(R.drawable.transparent_background)

            viewModel.selectedTab = PlaceListViewModel.SelectedTab.FAVORITE
            mainBinding.recycleSearchResult.recycledViewPool.clear()
            adapter.itemList = viewModel.recentPlaces.value?.stream()?.filter { place -> place.first.isFavorite}
                ?.collect(Collectors.toList())
            adapter.notifyDataSetChanged()
        }
    }

    override fun onResume() {
        super.onResume()

        mainBinding.etSearch.setText("")
    }
}
