package com.yapyap.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.yapyap.core.theme.YapSpacing

@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    return remember(translate, base, highlight) {
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(translate - 400f, translate - 400f),
            end = Offset(translate, translate),
        )
    }
}

@Composable
fun ShimmerConversationRow(
    modifier: Modifier = Modifier,
) {
    val brush = rememberShimmerBrush()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = YapSpacing.sm, vertical = YapSpacing.xs + YapSpacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(brush),
        )
        Spacer(modifier = Modifier.width(YapSpacing.sm - YapSpacing.xxs))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(YapSpacing.xxs))
                    .background(brush),
            )
            Spacer(modifier = Modifier.height(YapSpacing.xs))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(YapSpacing.xxs))
                    .background(brush),
            )
        }
    }
}

@Composable
fun ShimmerConversationList(
    modifier: Modifier = Modifier,
    itemCount: Int = 6,
) {
    Column(modifier = modifier) {
        repeat(itemCount) {
            ShimmerConversationRow()
        }
    }
}
