package com.yapyap.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yapyap.core.theme.YapYapTheme
import com.yapyap.core.ui.YapAvatar
import com.yapyap.feature.auth.presentation.AuthForm
import com.yapyap.feature.auth.presentation.AuthMode
import com.yapyap.feature.auth.presentation.AuthUiState

@Preview(showBackground = true, name = "Login")
@Composable
private fun LoginFormPreview() {
    YapYapTheme {
        AuthForm(
            title = "Yap Yap",
            subtitle = "Sign in to continue",
            mode = AuthMode.Login,
            uiState = AuthUiState(mode = AuthMode.Login),
            submitLabel = "Sign in",
            onNameChange = {},
            onEmailChange = {},
            onLoginIdentifierChange = {},
            onPasswordChange = {},
            onPhoneChange = {},
            onSubmit = {},
            alternateActionLabel = "Create an account",
            onAlternateAction = {},
        )
    }
}

@Preview(showBackground = true, name = "Avatar")
@Composable
private fun AvatarPreview() {
    YapYapTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            YapAvatar(label = "Yap Yap", size = 88.dp)
        }
    }
}

@Preview(showBackground = true, name = "Read receipt")
@Composable
private fun ReadReceiptPreview() {
    YapYapTheme {
        Text(
            text = "✓✓",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
