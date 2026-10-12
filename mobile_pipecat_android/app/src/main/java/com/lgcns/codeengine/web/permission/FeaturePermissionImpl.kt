package com.lgcns.codeengine.web.permission

import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import com.lgcns.codeengine.web.contract.GoAppDetailsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import timber.log.Timber

private typealias GrantResult = Map<String, Boolean>
private typealias GrantCallback = (Boolean) -> Unit

class FeaturePermissionImpl(
    private val helper: PermissionHelper,
    private val permissions: Array<Permission>,
    private val rationaleMessage: (denied: Array<Permission>) -> CharSequence,
    private val settingMessage: (denied: Array<Permission>) -> CharSequence,
    private val uiInteraction: FeaturePermissionUiInteraction,
) : FeaturePermission {

    private val requestPermissions =
        helper.activityResultCaller.registerForActivityResult(RequestMultiplePermissions()) {
            handlePermissionResult(it)
        }

    private val goAppDetailsSettings =
        helper.activityResultCaller.registerForActivityResult(GoAppDetailsSettings()) {
            handleAppDetailsSettingsResult()
        }

    private var grantFlow: Flow<Boolean>? = null
    private var grantCallback: GrantCallback? = null

    override suspend fun grant(): Boolean {
        val theGrantFlow = grantFlow ?: callbackFlow {
            grantCallback = { granted -> trySend(granted) }
            when {
                permissions.allGranted() -> onGranted()
                permissions.shouldShowRequestPermissionRationale() -> confirmRequestPermissionRationale()
                else -> requestPermissions.launch(permissions)
            }
            awaitClose { grantCallback = null }
        }.shareIn(
            scope = CoroutineScope(Dispatchers.Main),
            started = SharingStarted.WhileSubscribed(),
        ).onCompletion {
            grantFlow = null
        }.also { newGrantFlow ->
            grantFlow = newGrantFlow
        }
        return theGrantFlow.first()
    }

    override fun isGranted(): Boolean {
        return permissions.allGranted()
    }

    private fun onGranted() {
        grantCallback?.invoke(true)
    }

    private fun onDenied() {
        grantCallback?.invoke(false)
    }

    private fun handlePermissionResult(result: GrantResult) {
        Timber.d("keys = ${result.keys}")
        Timber.d("values = ${result.values}")

        when {
            result.isEmpty() -> onDenied() // cancelled
            result.allGranted() -> onGranted()
            result.shouldShowRequestPermissionRationale() -> onDenied()
            else -> confirmGoAppDetailsSettings()  // do not ask again
        }
    }

    private fun handleAppDetailsSettingsResult() {
        when {
            permissions.allGranted() -> onGranted()
            else -> onDenied()
        }
    }

    private fun confirmRequestPermissionRationale() {
        uiInteraction.confirmRequestPermissionRationale(
            message = rationaleMessage(permissions.deniedPermissions()),
            onConfirmed = { confirmed ->
                if (confirmed) requestPermissions.launch(permissions) else onDenied()
            }
        )
    }

    private fun confirmGoAppDetailsSettings() {
        uiInteraction.confirmGoAppDetailsSettings(
            message = settingMessage(permissions.deniedPermissions()),
            onConfirmed = { confirmed ->
                if (confirmed) goAppDetailsSettings.launch(helper.packageName) else onDenied()
            }
        )
    }

    private fun Permission.isDenied(): Boolean =
        helper.checkSelfPermission(this) == PackageManager.PERMISSION_DENIED

    private fun Permission.isGranted(): Boolean =
        helper.checkSelfPermission(this) == PackageManager.PERMISSION_GRANTED

    private fun Array<Permission>.allGranted(): Boolean = all { it.isGranted() }

    private fun Array<Permission>.shouldShowRequestPermissionRationale(): Boolean =
        any { helper.shouldShowRequestPermissionRationale(it) }

    private fun Array<Permission>.deniedPermissions(): Array<Permission> =
        filter { it.isDenied() }.toTypedArray()

    private fun GrantResult.allGranted(): Boolean =
        all { it.value }

    private fun GrantResult.shouldShowRequestPermissionRationale(): Boolean =
        any { (permission, _) ->
            helper.shouldShowRequestPermissionRationale(permission)
        }
}