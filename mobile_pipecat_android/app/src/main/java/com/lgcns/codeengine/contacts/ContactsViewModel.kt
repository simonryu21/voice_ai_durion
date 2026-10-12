package com.lgcns.codeengine.contacts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.lgcns.codeengine.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactManager: ContactManager
) : BaseViewModel() {
    private val _contactList = MutableLiveData<List<ContactData>>()
    val contactList: LiveData<List<ContactData>> = _contactList

    fun onItemViewSelected(position: Int) {
        Timber.e("아이템 클릭 :$position")
    }

    fun fetchContactList() {
        val list = contactManager.getContactList()
        _contactList.postValue(list)
    }
}