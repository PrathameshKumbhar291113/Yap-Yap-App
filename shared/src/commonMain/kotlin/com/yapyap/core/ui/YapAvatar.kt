package com.yapyap.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun YapAvatar(
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val initials = label.toAvatarInitials()
    val fontSize = (size.value * 0.34f).sp

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

fun String.toAvatarInitials(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return "?"
    val parts = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> {
            val first = parts[0].firstOrNull() ?: '?'
            val second = parts[1].firstOrNull() ?: '?'
            "$first$second".uppercase()
        }
        trimmed.contains("@") -> trimmed.substringBefore("@").take(2).uppercase().ifEmpty { "?" }
        else -> trimmed.take(2).uppercase().ifEmpty { "?" }
    }
}
