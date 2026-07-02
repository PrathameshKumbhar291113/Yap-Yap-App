package com.yapyap.core.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yapyap.feature.call.domain.entity.CallType
import com.yapyap.feature.call.presentation.ActivityHolder
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

class AndroidCallPermissionGateway(
    private val appContext: Context,
) : CallPermissionGateway {
    override suspend fun ensurePermissions(callType: CallType): CallPermissionStatus {
        val required = requiredPermissions(callType)
        if (required.all { isGranted(it) }) {
            if (callType == CallType.Video && isGranted(Manifest.permission.CAMERA)) {
                CallDiagnostics.stage(stage = "CALL_CAMERA_PERMISSION_GRANTED")
            }
            return CallPermissionStatus.Granted
        }

        val activity = ActivityHolder.current
            ?: return if (required.any { isGranted(it) }) {
                CallPermissionStatus.Denied
            } else {
                CallPermissionStatus.Denied
            }

        val deferred = CompletableDeferred<CallPermissionStatus>()
        val pending = PendingCallPermissionRequest(
            permissions = required.toTypedArray(),
            deferred = deferred,
        )
        if (!CallPermissionRequestRegistry.register(pending)) {
            return CallPermissionStatus.Denied
        }
        activity.runOnUiThread {
            CallPermissionRequestRegistry.launch(pending)
        }
        return deferred.await()
    }

    override fun openAppSettings() {
        val context = ActivityHolder.current ?: appContext
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requiredPermissions(callType: CallType): List<String> {
        return buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (callType == CallType.Video) {
                add(Manifest.permission.CAMERA)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    }
}

data class PendingCallPermissionRequest(
    val permissions: Array<String>,
    val deferred: CompletableDeferred<CallPermissionStatus>,
)

object CallPermissionRequestRegistry {
    private val launcherHolder = AtomicReference<CallPermissionLauncher?>(null)
    private val pendingRequest = AtomicReference<PendingCallPermissionRequest?>(null)

    fun bindLauncher(launcher: CallPermissionLauncher) {
        launcherHolder.set(launcher)
    }

    fun register(request: PendingCallPermissionRequest): Boolean {
        return pendingRequest.compareAndSet(null, request)
    }

    fun launch(request: PendingCallPermissionRequest) {
        val launcher = launcherHolder.get()
        if (launcher == null) {
            complete(request, CallPermissionStatus.Denied)
            return
        }
        launcher.launch(request.permissions)
    }

    fun onResult(grantResults: Map<String, Boolean>) {
        val request = pendingRequest.getAndSet(null) ?: return
        val allGranted = request.permissions.all { grantResults[it] == true }
        if (allGranted) {
            if (request.permissions.contains(Manifest.permission.CAMERA)) {
                CallDiagnostics.stage(stage = "CALL_CAMERA_PERMISSION_GRANTED")
            }
            complete(request, CallPermissionStatus.Granted)
            return
        }
        val activity = ActivityHolder.current
        val permanentlyDenied = activity != null && request.permissions.any { permission ->
            grantResults[permission] != true &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
        complete(
            request,
            if (permanentlyDenied) CallPermissionStatus.PermanentlyDenied else CallPermissionStatus.Denied,
        )
    }

    private fun complete(request: PendingCallPermissionRequest, status: CallPermissionStatus) {
        if (!request.deferred.isCompleted) {
            request.deferred.complete(status)
        }
    }
}

fun interface CallPermissionLauncher {
    fun launch(permissions: Array<String>)
}
