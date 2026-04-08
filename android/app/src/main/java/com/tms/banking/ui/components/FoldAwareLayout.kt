package com.tms.banking.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.map

data class FoldState(
    val isUnfolded: Boolean,
    val isSeparating: Boolean
)

@Composable
fun rememberFoldState(): FoldState {
    val context = LocalContext.current
    val windowLayoutInfo by WindowInfoTracker
        .getOrCreate(context)
        .windowLayoutInfo(context as androidx.activity.ComponentActivity)
        .map { info -> info.toFoldState() }
        .collectAsState(initial = FoldState(isUnfolded = false, isSeparating = false))

    return windowLayoutInfo
}

private fun WindowLayoutInfo.toFoldState(): FoldState {
    val foldingFeature = displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
    return FoldState(
        isUnfolded = foldingFeature?.state == FoldingFeature.State.FLAT,
        isSeparating = foldingFeature?.isSeparating == true
    )
}

@Composable
fun FoldAwareLayout(
    foldState: FoldState = rememberFoldState(),
    foldedContent: @Composable () -> Unit,
    unfoldedStart: @Composable () -> Unit,
    unfoldedEnd: @Composable () -> Unit
) {
    if (foldState.isUnfolded) {
        androidx.compose.foundation.layout.Row {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier.weight(1f)
            ) {
                unfoldedStart()
            }
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier.weight(1f)
            ) {
                unfoldedEnd()
            }
        }
    } else {
        foldedContent()
    }
}
