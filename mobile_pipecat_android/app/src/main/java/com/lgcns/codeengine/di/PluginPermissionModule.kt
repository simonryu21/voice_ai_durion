package com.lgcns.codeengine.di

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi
import com.lgcns.codeengine.ui.common.FeaturePermissionMessage
import com.lgcns.codeengine.web.*
import com.lgcns.codeengine.web.permission.ActivityPermissionHelper
import com.lgcns.codeengine.web.permission.FeaturePermission
import com.lgcns.codeengine.web.permission.FeaturePermissionImpl
import com.lgcns.codeengine.web.permission.FeaturePermissionUiInteraction
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Named

@Module
@InstallIn(ActivityComponent::class)
class PluginPermissionModule {

    @Named(PERMISSION_PLUGIN_DEVICE_EMAIL_ADDRESS)
    @Provides
    @ActivityScoped
    fun provideGetEmailAddressPermission(
        helper: ActivityPermissionHelper,
        uiInteraction: FeaturePermissionUiInteraction,
    ): FeaturePermission {
        return FeaturePermissionImpl(
            helper = helper,
            permissions = arrayOf(Manifest.permission.GET_ACCOUNTS),
            rationaleMessage = { deniedPermissions ->
                FeaturePermissionMessage.getRationaleMessage(helper.resources, deniedPermissions)
            },
            settingMessage = { deniedPermissions ->
                FeaturePermissionMessage.getSettingMessage(helper.resources, deniedPermissions)
            },
            uiInteraction = uiInteraction
        )
    }

    @Named(PERMISSION_PLUGIN_ALL_CONTACTS)
    @Provides
    @ActivityScoped
    fun provideGetAllContactsPermission(
        helper: ActivityPermissionHelper,
        uiInteraction: FeaturePermissionUiInteraction,
    ): FeaturePermission {
        return FeaturePermissionImpl(
            helper = helper,
            permissions = arrayOf(Manifest.permission.READ_CONTACTS),
            rationaleMessage = { deniedPermissions ->
                FeaturePermissionMessage.getRationaleMessage(helper.resources, deniedPermissions)
            },
            settingMessage = { deniedPermissions ->
                FeaturePermissionMessage.getSettingMessage(helper.resources, deniedPermissions)
            },
            uiInteraction = uiInteraction
        )
    }

    @Named(PERMISSION_PLUGIN_WRITE_PUBLIC_PICTURES)
    @Provides
    @ActivityScoped
    fun provideWritePublicPicturesPermission(
        helper: ActivityPermissionHelper,
        uiInteraction: FeaturePermissionUiInteraction,
    ): FeaturePermission {
        val permissions = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            emptyArray()
        } else {
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        return FeaturePermissionImpl(
            helper = helper,
            permissions = permissions,
            rationaleMessage = { deniedPermissions ->
                FeaturePermissionMessage.getRationaleMessage(helper.resources, deniedPermissions)
            },
            settingMessage = { deniedPermissions ->
                FeaturePermissionMessage.getSettingMessage(helper.resources, deniedPermissions)
            },
            uiInteraction = uiInteraction
        )
    }

    @Named(PERMISSION_PLUGIN_FIND_LOCATION)
    @Provides
    @ActivityScoped
    fun provideFindLocationPermission(
        helper: ActivityPermissionHelper,
        uiInteraction: FeaturePermissionUiInteraction,
    ): FeaturePermission {
        return FeaturePermissionImpl(
            helper = helper,
            permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            rationaleMessage = { deniedPermissions ->
                FeaturePermissionMessage.getRationaleMessage(helper.resources, deniedPermissions)
            },
            settingMessage = { deniedPermissions ->
                FeaturePermissionMessage.getSettingMessage(helper.resources, deniedPermissions)
            },
            uiInteraction = uiInteraction
        )
    }

    @Named(PERMISSION_PLUGIN_GET_PHONE_NUMBER)
    @Provides
    @ActivityScoped
    fun provideGetPhoneNumberPermission(
        helper: ActivityPermissionHelper,
        uiInteraction: FeaturePermissionUiInteraction,
    ): FeaturePermission {

        return FeaturePermissionImpl(
            helper = helper,
            permissions = run {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    arrayOf(Manifest.permission.READ_PHONE_NUMBERS)
                } else {
                    arrayOf(Manifest.permission.READ_PHONE_STATE)
                }
            },
            rationaleMessage = { deniedPermissions ->
                FeaturePermissionMessage.getRationaleMessage(helper.resources, deniedPermissions)
            },
            settingMessage = { deniedPermissions ->
                FeaturePermissionMessage.getSettingMessage(helper.resources, deniedPermissions)
            },
            uiInteraction = uiInteraction
        )
    }

    @Named(PERMISSION_PLUGIN_RECORD_AUDIO)
    @Provides
    @ActivityScoped
    fun provideRecordAudioPermission(
        helper: ActivityPermissionHelper,
        uiInteraction: FeaturePermissionUiInteraction,
    ): FeaturePermission {
        return FeaturePermissionImpl(
            helper = helper,
            permissions = run {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                        // Android 13 이상: READ_MEDIA_AUDIO 사용
                        arrayOf(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.READ_MEDIA_AUDIO,
                            Manifest.permission.INTERNET
                        )
                    }
                    Build.VERSION.SDK_INT > Build.VERSION_CODES.P -> {
                        // Android 10-12: READ_EXTERNAL_STORAGE 사용
                        arrayOf(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.INTERNET
                        )
                    }
                    else -> {
                        // Android 9 이하: WRITE_EXTERNAL_STORAGE도 필요
                        arrayOf(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.INTERNET
                        )
                    }
                }
            },
            rationaleMessage = { deniedPermissions ->
                FeaturePermissionMessage.getRationaleMessage(helper.resources, deniedPermissions)
            },
            settingMessage = { deniedPermissions ->
                FeaturePermissionMessage.getSettingMessage(helper.resources, deniedPermissions)
            },
            uiInteraction = uiInteraction
        )
    }

    @Named(PERMISSION_PLUGIN_CALENDAR_ACCESS)
    @Provides
    @ActivityScoped
    fun provideCalendarAccessPermission(
        helper: ActivityPermissionHelper,
        uiInteraction: FeaturePermissionUiInteraction,
    ): FeaturePermission {
        return FeaturePermissionImpl(
            helper = helper,
            permissions = arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            ),
            rationaleMessage = { deniedPermissions ->
                FeaturePermissionMessage.getRationaleMessage(helper.resources, deniedPermissions)
            },
            settingMessage = { deniedPermissions ->
                FeaturePermissionMessage.getSettingMessage(helper.resources, deniedPermissions)
            },
            uiInteraction = uiInteraction
        )
    }

    @Named(PERMISSION_PLUGIN_CAMERA)
    @Provides
    @ActivityScoped
    fun provideCameraPermission(
        helper: ActivityPermissionHelper,
        uiInteraction: FeaturePermissionUiInteraction,
    ): FeaturePermission {
        return FeaturePermissionImpl(
            helper = helper,
            permissions = arrayOf(
                Manifest.permission.CAMERA,
            ),
            rationaleMessage = { deniedPermissions ->
                FeaturePermissionMessage.getRationaleMessage(helper.resources, deniedPermissions)
            },
            settingMessage = { deniedPermissions ->
                FeaturePermissionMessage.getSettingMessage(helper.resources, deniedPermissions)
            },
            uiInteraction = uiInteraction
        )
    }

    @Named(PERMISSION_PLUGIN_PHONE_CALL_STATE)
    @Provides
    @ActivityScoped
    fun providePhoneCallStatePermission(
        helper: ActivityPermissionHelper,
        uiInteraction: FeaturePermissionUiInteraction,
    ): FeaturePermission {
        return FeaturePermissionImpl(
            helper = helper,
            permissions = arrayOf(
                Manifest.permission.READ_PHONE_STATE,
            ),
            rationaleMessage = { deniedPermissions ->
                FeaturePermissionMessage.getRationaleMessage(helper.resources, deniedPermissions)
            },
            settingMessage = { deniedPermissions ->
                FeaturePermissionMessage.getSettingMessage(helper.resources, deniedPermissions)
            },
            uiInteraction = uiInteraction
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Named(PERMISSION_PLUGIN_POST_NOTIFICATIONS)
    @Provides
    @ActivityScoped
    fun providePostNotificationPermission(
        helper: ActivityPermissionHelper,
        uiInteraction: FeaturePermissionUiInteraction,
    ): FeaturePermission {
        return FeaturePermissionImpl(
            helper = helper,
            permissions = arrayOf(
                Manifest.permission.POST_NOTIFICATIONS
            ),
            rationaleMessage = { deniedPermissions ->
                FeaturePermissionMessage.getRationaleMessage(helper.resources, deniedPermissions)
            },
            settingMessage = { deniedPermissions ->
                FeaturePermissionMessage.getSettingMessage(helper.resources, deniedPermissions)
            },
            uiInteraction = uiInteraction
        )
    }

    @Named(PERMISSION_PLUGIN_NFC)
    @Provides
    @ActivityScoped
    fun provideNfcPermission(
        helper: ActivityPermissionHelper,
        uiInteraction: FeaturePermissionUiInteraction,
    ): FeaturePermission {
        return FeaturePermissionImpl(
            helper = helper,
            permissions = arrayOf(
                Manifest.permission.NFC,
            ),
            rationaleMessage = { deniedPermissions ->
                FeaturePermissionMessage.getRationaleMessage(helper.resources, deniedPermissions)
            },
            settingMessage = { deniedPermissions ->
                FeaturePermissionMessage.getSettingMessage(helper.resources, deniedPermissions)
            },
            uiInteraction = uiInteraction
        )
    }
}