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
import yapyap.shared.generated.resources.ic_arrow_back
import yapyap.shared.generated.resources.ic_chat
import yapyap.shared.generated.resources.ic_person
import yapyap.shared.generated.resources.ic_send

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
    val Send = Res.drawable.ic_send
    val ArrowBack = Res.drawable.ic_arrow_back
    val Chat = Res.drawable.ic_chat
    val Person = Res.drawable.ic_person
    val Add = Res.drawable.ic_add
}
