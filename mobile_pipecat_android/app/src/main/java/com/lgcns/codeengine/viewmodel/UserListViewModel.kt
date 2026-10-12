package com.lgcns.codeengine.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lgcns.codeengine.data.Result
import com.lgcns.codeengine.data.entity.User
import com.lgcns.codeengine.data.repository.CodeEngineRepository
import com.lgcns.codeengine.util.eventflow.MutableEventFlow
import com.lgcns.codeengine.util.eventflow.asEventFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class UserListViewModel @Inject constructor() : BaseViewModel() {

    @Inject
    lateinit var repository: CodeEngineRepository

    val userList = MutableLiveData<Result<List<User>>>()

    private val _itemSelected = MutableEventFlow<Any>()
    val itemSelected = _itemSelected.asEventFlow()

    fun getUsers() {
        launchWithProgressBar {
            userList.value = repository.getUsers()
        }
    }

    fun onItemViewSelected(position: Int) {
        Timber.d("onItemViewSelected() selected index: $position")

        viewModelScope.launch {
            _itemSelected.emit(1)
        }
    }
}