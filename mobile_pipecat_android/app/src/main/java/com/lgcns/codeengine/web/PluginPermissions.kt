package com.lgcns.codeengine.web

import com.lgcns.codeengine.web.permission.FeaturePermission
import javax.inject.Inject
import javax.inject.Named

const val PERMISSION_PLUGIN_DEVICE_EMAIL_ADDRESS = "perm.plugin.DeviceEmailAddress"
const val PERMISSION_PLUGIN_ALL_CONTACTS = "perm.plugin.AllContacts"
const val PERMISSION_PLUGIN_WRITE_PUBLIC_PICTURES = "perm.plugin.WRITE_PUBLIC_PICTURES"
const val PERMISSION_PLUGIN_FIND_LOCATION = "perm.plugin.FindLocation"
const val PERMISSION_PLUGIN_GET_PHONE_NUMBER = "perm.plugin.GET_PHONE_NUMBER"
const val PERMISSION_PLUGIN_RECORD_AUDIO = "perm.plugin.RecordAudio"
const val PERMISSION_PLUGIN_CALENDAR_ACCESS = "perm.plugin.CalendarAccess"
const val PERMISSION_PLUGIN_CAMERA = "perm.plugin.Camera"
const val PERMISSION_PLUGIN_PHONE_CALL_STATE = "perm.plugin.PhoneCallState"
const val PERMISSION_PLUGIN_POST_NOTIFICATIONS = "perm.plugin.Notification"
const val PERMISSION_PLUGIN_NFC = "perm.plugin.Nfc"

class PluginPermissions @Inject constructor(
    @Named(PERMISSION_PLUGIN_DEVICE_EMAIL_ADDRESS) val deviceEmailAddress: FeaturePermission,
    @Named(PERMISSION_PLUGIN_ALL_CONTACTS) val allContactsPermission: FeaturePermission,
    @Named(PERMISSION_PLUGIN_WRITE_PUBLIC_PICTURES) val publicPicturesWritePermission: FeaturePermission,
    @Named(PERMISSION_PLUGIN_FIND_LOCATION) val locationPermission: FeaturePermission,
    @Named(PERMISSION_PLUGIN_GET_PHONE_NUMBER) val phoneNumberPermission: FeaturePermission,
    @Named(PERMISSION_PLUGIN_RECORD_AUDIO) val recordAudioPermission: FeaturePermission,
    @Named(PERMISSION_PLUGIN_CALENDAR_ACCESS) val calendarAccessPermission: FeaturePermission,
    @Named(PERMISSION_PLUGIN_CAMERA) val cameraPermission: FeaturePermission,
    @Named(PERMISSION_PLUGIN_PHONE_CALL_STATE) val phoneCallStatePermission: FeaturePermission,
    @Named(PERMISSION_PLUGIN_POST_NOTIFICATIONS) val notificationPermission: FeaturePermission,
    @Named(PERMISSION_PLUGIN_NFC) val nfcPermission: FeaturePermission,
)

data class PermissionData (
    val permissionName: String,
    val pluginPermission: FeaturePermission,
)

