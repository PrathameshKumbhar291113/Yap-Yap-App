package com.yapyap.feature.splash.presentation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yapyap.core.theme.YapSpacing
import com.yapyap.core.session.BootstrapState
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.app_name

@Composable
fun SplashScreen(
    bootstrapState: BootstrapState,
    onNavigateToAuth: () -> Unit,
    onNavigateToMain: () -> Unit,
) {
    LaunchedEffect(bootstrapState) {
        if (bootstrapState == BootstrapState.Checking) return@LaunchedEffect
        delay(SPLASH_MIN_DISPLAY_MS)
        when (bootstrapState) {
            BootstrapState.Authenticated -> onNavigateToMain()
            BootstrapState.Unauthenticated -> onNavigateToAuth()
            BootstrapState.Checking -> Unit
        }
    }

    val transition = rememberInfiniteTransition(label = "splash_logo")
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splash_scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splash_alpha",
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .scale(scale)
                    .alpha(alpha),
            )
            if (bootstrapState == BootstrapState.Checking) {
                Spacer(modifier = Modifier.height(YapSpacing.md))
                CircularProgressIndicator()
            }
        }
    }
}

private const val SPLASH_MIN_DISPLAY_MS = 700L
