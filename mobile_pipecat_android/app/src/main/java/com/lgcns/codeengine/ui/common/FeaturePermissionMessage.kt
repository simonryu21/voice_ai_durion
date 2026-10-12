package com.lgcns.codeengine.ui.common

import android.Manifest
import android.content.res.Resources
import com.lgcns.codeengine.R
import com.lgcns.codeengine.web.permission.Permission

object FeaturePermissionMessage {

    fun getRationaleMessage(res: Resources, deniedPermissions: Array<Permission>): CharSequence {
        val names = deniedPermissions.joinToPermissionNames(res)
        return res.getString(R.string.feature_permission_guide_msg, names)
    }

    fun getSettingMessage(res: Resources, denied: Array<Permission>): CharSequence {
        val names = denied.joinToPermissionNames(res)
        return res.getString(R.string.feature_permission_guide_msg, names)
    }

    private fun Array<Permission>.joinToPermissionNames(res: Resources): String =
        joinToString(", ") { permission ->
            val labelResId = when (permission) {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO -> R.string.permission_storage
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.READ_PHONE_STATE -> R.string.permission_phone
                Manifest.permission.READ_CONTACTS -> R.string.permission_contacts
                Manifest.permission.CAMERA -> R.string.permission_camera
                Manifest.permission.RECORD_AUDIO -> R.string.permission_microphone
                Manifest.permission.MODIFY_AUDIO_SETTINGS -> R.string.permission_audio_settings
                Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR -> R.string.permission_calendar
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION -> R.string.permission_location
                Manifest.permission.GET_ACCOUNTS -> R.string.permission_device_accounts
                Manifest.permission.POST_NOTIFICATIONS -> R.string.permission_notification
                else -> null
            }
            if (labelResId != null) res.getString(labelResId) else permission
        }
}