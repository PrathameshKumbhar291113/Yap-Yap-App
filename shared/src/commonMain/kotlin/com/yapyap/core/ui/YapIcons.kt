package com.yapyap.core.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.ic_add
import yapyap.shared.generated.resources.ic_app_logo
import yapyap.shared.generated.resources.ic_arrow_back
import yapyap.shared.generated.resources.ic_chat
import yapyap.shared.generated.resources.ic_chevron_right
import yapyap.shared.generated.resources.ic_description
import yapyap.shared.generated.resources.ic_edit
import yapyap.shared.generated.resources.ic_help
import yapyap.shared.generated.resources.ic_info
import yapyap.shared.generated.resources.ic_lock
import yapyap.shared.generated.resources.ic_notifications
import yapyap.shared.generated.resources.ic_palette
import yapyap.shared.generated.resources.ic_person
import yapyap.shared.generated.resources.ic_send
import yapyap.shared.generated.resources.ic_shield

@Composable
fun YapIcon(
    resource: DrawableResource,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(resource),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

object YapDrawable {
    val AppLogo = Res.drawable.ic_app_logo
    val Send = Res.drawable.ic_send
    val ArrowBack = Res.drawable.ic_arrow_back
    val Chat = Res.drawable.ic_chat
    val Person = Res.drawable.ic_person
    val Add = Res.drawable.ic_add
    val Edit = Res.drawable.ic_edit
    val Notifications = Res.drawable.ic_notifications
    val Palette = Res.drawable.ic_palette
    val Lock = Res.drawable.ic_lock
    val Shield = Res.drawable.ic_shield
    val Description = Res.drawable.ic_description
    val Help = Res.drawable.ic_help
    val Info = Res.drawable.ic_info
    val ChevronRight = Res.drawable.ic_chevron_right
}
