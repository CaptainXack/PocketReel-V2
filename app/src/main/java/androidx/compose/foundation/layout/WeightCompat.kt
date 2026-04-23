package androidx.compose.foundation.layout

import androidx.compose.ui.Modifier

@Suppress("UnusedReceiverParameter")
fun Modifier.weight(
    @Suppress("UNUSED_PARAMETER") weight: Float,
    @Suppress("UNUSED_PARAMETER") fill: Boolean = true,
): Modifier = this.fillMaxWidth(0.8f)
