package io.leaflet.reader.ui.theme

import android.view.View
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

/**
 * The app's confirmation dialog, with the display profile's visual policy applied in one place
 * (REQ-0004 FR-4, AGENTS "avoid transparency, shadows … disable crossfades").
 *
 * Under a high-contrast profile (E-Ink) the platform's translucent dim behind the dialog and
 * its window fade are removed, the container is the solid page colour with no tonal tint, and
 * a 2 dp outline separates the dialog from the page it now sits on without a scrim. Standard
 * keeps the platform dialog exactly as before.
 *
 * Dim and window-animation removal act on the dialog window itself; whether the panel shows
 * no residual transition is only observable on a device and is unverified.
 */
@Composable
fun LeafletAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    val solid = LocalDisplayProfile.current.highContrastEnabled
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            if (solid) StaticDialogWindow()
            title()
        },
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        modifier =
            if (solid) {
                Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), MaterialTheme.shapes.extraLarge)
            } else {
                Modifier
            },
        containerColor = if (solid) MaterialTheme.colorScheme.surface else AlertDialogDefaults.containerColor,
        tonalElevation = if (solid) 0.dp else AlertDialogDefaults.TonalElevation,
    )
}

/** Removes the dim and the window animation of the dialog this composable is hosted in. */
@Composable
private fun StaticDialogWindow() {
    val window = dialogWindowOf(LocalView.current)
    SideEffect {
        window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0f)
            setWindowAnimations(0)
        }
    }
}

/** Walks up from [view] to the Compose dialog layout, or null when it is not inside a dialog. */
private fun dialogWindowOf(view: View): Window? {
    var node: Any? = view
    while (node != null) {
        if (node is DialogWindowProvider) return node.window
        node =
            when (node) {
                is View -> node.parent
                is ViewParent -> node.parent
                else -> null
            }
    }
    return null
}
