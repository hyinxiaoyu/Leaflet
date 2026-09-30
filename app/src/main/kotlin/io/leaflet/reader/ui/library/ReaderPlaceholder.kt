package io.leaflet.reader.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.leaflet.reader.R

/**
 * Reader placeholder (T05 navigation target, T06/T07 replace the body).
 *
 * Exists so library document rows have a stable `reader/{documentId}` route
 * to navigate to; the real Reader binds here in T07
 * (`:core:reader` `ReaderSession`/`ReaderViewState` per T06 handoff).
 * No content parsing happens in this stub.
 */
@Composable
fun ReaderPlaceholderScreen(
    documentId: String,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.reader_placeholder_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.reader_placeholder_body, documentId))
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) { Text(stringResource(R.string.go_back)) }
    }
}
