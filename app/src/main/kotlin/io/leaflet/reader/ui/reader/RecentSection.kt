package io.leaflet.reader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.leaflet.reader.R

/** Compatibility entry point; Home binds the same rows into its own single lazy list. */
@Composable
fun RecentSection(
    viewModel: RecentViewModel,
    onOpenDocument: (String) -> Unit,
) {
    val rows by viewModel.rows.collectAsState()
    val notice by viewModel.notice.collectAsState()
    if (rows.isEmpty() && notice == null) return
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.recent_title), style = MaterialTheme.typography.titleMedium)
        notice?.let {
            Text(it)
            TextButton(onClick = { viewModel.dismissNotice() }) { Text(stringResource(R.string.dismiss)) }
        }
        rows.forEach { row ->
            RecentReadRow(row, onOpenDocument, { viewModel.removeEntry(io.leaflet.model.DocumentId(it)) })
        }
    }
}

/** Shared rendering preserves unavailable entries and the original document-id handoff. */
@Composable
internal fun RecentReadRow(
    row: RecentRow,
    onOpenDocument: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp)
            .clickable { onOpenDocument(row.entry.documentId.value) }
            .padding(vertical = 12.dp),
    ) {
        Text(row.entry.titleSnapshot, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(recentSubtitle(row), style = MaterialTheme.typography.bodySmall)
        when (row.health) {
            RecentHealth.GONE -> {
                Text(stringResource(R.string.recent_gone_hint), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onRemove(row.entry.documentId.value) }) { Text(stringResource(R.string.reader_remove_dead_entry)) }
            }
            RecentHealth.AUTH_LOST -> Text(stringResource(R.string.recent_auth_lost_hint), style = MaterialTheme.typography.bodySmall)
            RecentHealth.UNKNOWN, RecentHealth.OK -> Unit
        }
    }
}

@Composable
internal fun recentSubtitle(row: RecentRow): String {
    val library = row.libraryName ?: stringResource(R.string.recent_unknown_library)
    return "$library · ${relativeTime(row.entry.lastOpenedAtEpochMs)}"
}

/** Read once on composition; E-Ink gets no periodic clock updates. */
@Composable
private fun relativeTime(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val minutes = if (epochMs in 0..now) (now - epochMs) / 60_000L else 0L
    return when {
        minutes < 1 -> stringResource(R.string.recent_time_now)
        minutes < 60 -> stringResource(R.string.recent_time_minutes, minutes)
        minutes < 1440 -> stringResource(R.string.recent_time_hours, minutes / 60)
        else -> stringResource(R.string.recent_time_days, minutes / 1440)
    }
}
