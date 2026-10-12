package com.lgcns.codeengine.web.permission

import android.content.res.Resources
import androidx.activity.result.ActivityResultCaller

interface PermissionHelper {
    val packageName: String
    val activityResultCaller: ActivityResultCaller
    val shouldShowRequestPermissionRationale: (Permission) -> Boolean
    val checkSelfPermission: (Permission) -> Int
    val resources: Resources
    val canDrawOverlays: () -> Boolean
}