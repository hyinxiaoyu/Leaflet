package io.leaflet.reader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

/**
 * Recent-reads section for the Library screen (T07, REQ-0003 UX-3/UX-4):
 * title snapshot + library + relative time per row, health hints for
 * gone/unauthorized files, single-item removal + clear-all.
 *
 * Rows never vanish silently: a dead entry stays visible with its prompt
 * until the user confirms removal (REQ-0003 FR-6).
 */
@Composable
fun RecentSection(
    viewModel: RecentViewModel,
    onOpenDocument: (String) -> Unit,
) {
    val rows by viewModel.rows.collectAsState()
    val notice by viewModel.notice.collectAsState()
    if (rows.isEmpty() && notice == null) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.recent_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        notice?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { viewModel.dismissNotice() }) {
                Text(stringResource(R.string.dismiss))
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            for (row in rows) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { onOpenDocument(row.entry.documentId.value) }
                            .padding(vertical = 6.dp),
                ) {
                    Text(row.entry.titleSnapshot, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        recentSubtitle(row),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    when (row.health) {
                        RecentHealth.GONE -> {
                            Text(
                                stringResource(R.string.recent_gone_hint),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row {
                                TextButton(onClick = {
                                    viewModel.removeEntry(row.entry.documentId)
                                }) {
                                    Text(stringResource(R.string.reader_remove_dead_entry))
                                }
                            }
                        }
                        RecentHealth.AUTH_LOST -> {
                            Text(
                                stringResource(R.string.recent_auth_lost_hint),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        RecentHealth.UNKNOWN, RecentHealth.OK -> Unit
                    }
                }
            }
        }
        if (rows.isNotEmpty()) {
            TextButton(onClick = { viewModel.clearAll() }) {
                Text(stringResource(R.string.recent_clear_all))
            }
        }
    }
}

@Composable
private fun recentSubtitle(row: RecentRow): String {
    val lib = row.libraryName ?: stringResource(R.string.recent_unknown_library)
    return "$lib · ${relativeTime(row.entry.lastOpenedAtEpochMs)}"
}

/** Static relative-time label (no ticking clock: E-Ink friendly, no recomposition timer). */
private fun relativeTime(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMin = ((now - epochMs).coerceAtLeast(0L) / 60_000L).toInt()
    return when {
        diffMin < 1 -> "刚刚"
        diffMin < 60 -> "$diffMin 分钟前"
        diffMin < 60 * 24 -> "${diffMin / 60} 小时前"
        else -> "${diffMin / (60 * 24)} 天前"
    }
}
