package com.lgcns.codeengine.contacts

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.lgcns.codeengine.databinding.ActivityContactsBinding
import com.lgcns.codeengine.util.*
import com.lgcns.codeengine.web.contract.GoAppDetailsSettings
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ContactsActivity : AppCompatActivity() {
    /**
     * Todo
     * 퍼미션 관련 로직, 추후 따로 빼서 관리할 예정
     */
    private val requiredPermission =
        arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            handlePermissionResult(it)
        }
    private val appSettingLauncher =
        registerForActivityResult(GoAppDetailsSettings()) {
            checkPermission(true)
        }
    private val permissionGrantedCallback: () -> Unit = {
        viewModel.fetchContactList()
    }
    private val permissionDeniedCallback: (List<String>) -> Unit = {
        Toast.makeText(this, "${it.joinToString(",")}권한이 없어 종료합니다.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private val binding: ActivityContactsBinding by lazy {
        ActivityContactsBinding.inflate(layoutInflater)
    }

    private val adapter: ContactsListAdapter by lazy {
        ContactsListAdapter(this, viewModel)
    }

    private val viewModel: ContactsViewModel by lazy {
        ViewModelProvider(this)[ContactsViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        checkPermission()
        setUpViews()
        setObservers()
    }

    private fun setUpViews() {
        binding.rvContacts.adapter = adapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setObservers() {
        viewModel.contactList.observe(this) {
            adapter.itemList = it
            adapter.notifyDataSetChanged()
        }
    }

    private fun checkPermission(fromSettings: Boolean = false) {
        val deniedPermission = mutableListOf<String>()
        requiredPermission.forEach {
            if (checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED) {
                deniedPermission.add(it)
            }
        }
        if (deniedPermission.isEmpty()) {
            permissionGrantedCallback.invoke()
            return
        }
        if (fromSettings) {
            permissionDeniedCallback.invoke(deniedPermission)
            return
        } else {
            alert {
                setTitle("권한 요청")
                setMessage("Required Permission: ${deniedPermission.joinToString(",")}")
                positiveButton {
                    permissionLauncher.launch(deniedPermission.toTypedArray())
                }
            }
        }
    }

    private fun handlePermissionResult(map: Map<String, Boolean>) {
        val deniedPermission = map.filter { !it.value }.map { it.key }
        if (deniedPermission.isNotEmpty()) {
            alert {
                setTitle("Permission denied")
                setMessage("If you reject permission,you can not use this service\n\nPlease turn on permissions at [Setting] > [Permission]")
                positiveButton {
                    appSettingLauncher.launch(packageName)
                }
                negativeButton("닫기") {
                    permissionDeniedCallback.invoke(deniedPermission)
                }
            }
        }
    }
}