package com.yapyap.feature.call.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yapyap.core.theme.YapCallGreenLight
import com.yapyap.core.theme.YapSpacing
import com.yapyap.core.ui.YapDrawable
import com.yapyap.core.ui.YapIcon
import com.yapyap.feature.call.domain.entity.CallType
import org.jetbrains.compose.resources.stringResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.call_ongoing_video
import yapyap.shared.generated.resources.call_ongoing_voice
import yapyap.shared.generated.resources.call_return_to_call

@Composable
fun OngoingCallTopBar(
    callType: CallType?,
    remoteDisplayName: String,
    elapsedSeconds: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val label = when (callType) {
        CallType.Video -> stringResource(Res.string.call_ongoing_video)
        CallType.Voice, null -> stringResource(Res.string.call_ongoing_voice)
    }
    val timer = elapsedSeconds?.let { formatCallDuration(it) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(YapCallGreenLight)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White.copy(alpha = 0.2f)),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = YapSpacing.md, vertical = YapSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YapSpacing.sm),
    ) {
        YapIcon(
            resource = if (callType == CallType.Video) YapDrawable.Videocam else YapDrawable.Call,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color.White,
        )
        Text(
            text = buildString {
                append(label)
                if (!remoteDisplayName.isBlank()) {
                    append(" · ")
                    append(remoteDisplayName)
                }
                timer?.let {
                    append(" · ")
                    append(it)
                }
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Text(
            text = stringResource(Res.string.call_return_to_call),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.9f),
        )
    }
}
