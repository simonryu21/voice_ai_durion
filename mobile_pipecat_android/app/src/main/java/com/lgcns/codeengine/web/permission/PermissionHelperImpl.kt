package com.lgcns.codeengine.web.permission

import android.content.res.Resources
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCaller
import androidx.fragment.app.Fragment

class ActivityPermissionHelper(
    private val activity: ComponentActivity
) : PermissionHelper {

    override val packageName: String
        get() = activity.packageName

    override val activityResultCaller: ActivityResultCaller
        get() = activity

    override val shouldShowRequestPermissionRationale: (Permission) -> Boolean
        get() = activity::shouldShowRequestPermissionRationale

    override val checkSelfPermission: (Permission) -> Int
        get() = activity::checkSelfPermission

    override val resources: Resources
        get() = activity.resources

    override val canDrawOverlays: () -> Boolean
        get() = { Settings.canDrawOverlays(activity) }
}

class FragmentPermissionHelper(
    private val fragment: Fragment
) : PermissionHelper {

    override val packageName: String
        get() = fragment.requireContext().packageName

    override val activityResultCaller: ActivityResultCaller
        get() = fragment

    override val shouldShowRequestPermissionRationale: (Permission) -> Boolean
        get() = fragment::shouldShowRequestPermissionRationale

    override val checkSelfPermission: (Permission) -> Int
        get() = fragment.requireContext()::checkSelfPermission

    override val resources: Resources
        get() = fragment.resources

    override val canDrawOverlays: () -> Boolean
        get() = { Settings.canDrawOverlays(fragment.requireContext()) }
}