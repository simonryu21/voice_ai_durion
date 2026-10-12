package com.lgcns.codeengine.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.lgcns.codeengine.R
import com.lgcns.codeengine.adapter.UserListAdapter
import com.lgcns.codeengine.data.Result
import com.lgcns.codeengine.databinding.ActivityUserListBinding
import com.lgcns.codeengine.util.extension.collectRepeatOnStarted
import com.lgcns.codeengine.viewmodel.UserListViewModel
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * RemoteDataSource를 테스트할 수 있는 Activity입니다.
 * BASE_URL 서버에서 사용자 정보를 가져와 adapter로 보여주는 시나리오입니다.
 * 서버에서 데이터를 가져오는 동안 ProgressBar를 보여줍니다.
 */
@AndroidEntryPoint
class UserListActivity : AppCompatActivity() {

    private lateinit var mainBinding: ActivityUserListBinding

    private val viewModel: UserListViewModel by lazy {
        ViewModelProvider(this)[UserListViewModel::class.java]
    }

    private val adapter: UserListAdapter by lazy {
        UserListAdapter(this, viewModel)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("Start UserListActivity..")

        mainBinding = DataBindingUtil.setContentView(this, R.layout.activity_user_list)

        setView()
        setObserver()

        viewModel.getUsers()
    }

    private fun setView() {
        mainBinding.recyclerView.adapter = adapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setObserver() {
        viewModel.itemSelected.collectRepeatOnStarted(this) { event ->
            event.let {
                val intent = Intent(this, WebViewTestActivity::class.java)
                startActivity(intent)
                //finish() <-- back key 누르면 현재 activity로 돌아오게 하기 위해
            }
        }

        viewModel.userList.observe(this) {
            Timber.d("userList is observed")
            it.let {
                when (it) {
                    is Result.Success -> {
                        mainBinding.recyclerView.recycledViewPool.clear()
                        adapter.itemList = it.data
                        adapter.notifyDataSetChanged()
                    }
                    is Result.Error -> {
                        Toast.makeText(this, it.exception.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        viewModel.showProgress.observe(this) {
            if (it) {
                Timber.d("showProgress is true")
                mainBinding.progressBar.visibility = View.VISIBLE
                mainBinding.recyclerView.visibility = View.GONE
            } else {
                Timber.d("showProgress is false")
                mainBinding.recyclerView.visibility = View.VISIBLE
                mainBinding.progressBar.visibility = View.GONE
            }
        }
    }
}
