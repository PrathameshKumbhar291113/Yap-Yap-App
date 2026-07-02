package com.yapyap.feature.call.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.yapyap.core.call.CallPermissionStatus
import com.yapyap.feature.call.domain.entity.CallType
import org.jetbrains.compose.resources.stringResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.call_permission_grant
import yapyap.shared.generated.resources.call_permission_not_now
import yapyap.shared.generated.resources.call_permission_open_settings
import yapyap.shared.generated.resources.call_permission_rationale_camera
import yapyap.shared.generated.resources.call_permission_rationale_mic
import yapyap.shared.generated.resources.call_permission_rationale_title
import yapyap.shared.generated.resources.call_permission_settings_body
import yapyap.shared.generated.resources.call_permission_settings_title

@Composable
fun CallPermissionDialog(
    callType: CallType,
    status: CallPermissionStatus,
    onDismiss: () -> Unit,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (status == CallPermissionStatus.Granted) return

    val isPermanent = status == CallPermissionStatus.PermanentlyDenied
    val title = if (isPermanent) {
        stringResource(Res.string.call_permission_settings_title)
    } else {
        stringResource(Res.string.call_permission_rationale_title)
    }
    val body = if (isPermanent) {
        stringResource(Res.string.call_permission_settings_body)
    } else if (callType == CallType.Video) {
        stringResource(Res.string.call_permission_rationale_camera)
    } else {
        stringResource(Res.string.call_permission_rationale_mic)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = if (isPermanent) onOpenSettings else onGrant,
            ) {
                Text(
                    stringResource(
                        if (isPermanent) {
                            Res.string.call_permission_open_settings
                        } else {
                            Res.string.call_permission_grant
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.call_permission_not_now))
            }
        },
    )
}
