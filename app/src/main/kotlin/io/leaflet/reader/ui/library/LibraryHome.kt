package io.leaflet.reader.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.leaflet.model.LibraryId
import io.leaflet.reader.R
import io.leaflet.reader.ui.reader.RecentHealth
import io.leaflet.reader.ui.reader.RecentReadRow
import io.leaflet.reader.ui.reader.RecentRow
import io.leaflet.reader.ui.reader.RecentViewModel
import io.leaflet.reader.ui.reader.recentSubtitle
import io.leaflet.storage.ScanState

object HomeTags {
    const val LIST = "home-list"
    const val CONTINUE = "home-continue"
    const val SETTINGS = "home-settings"
    const val EMPTY = "home-empty"
    const val TOP_SETTINGS = "home-top-settings"
    const val CLEAR = "home-clear"
    const val CLEAR_CONFIRM = "home-clear-confirm"
    const val CLEAR_CANCEL = "home-clear-cancel"

    fun library(id: String): String = "home-library:$id"

    fun recent(id: String): String = "home-recent:$id"
}

@Composable
fun LibraryListScreen(
    viewModel: LibraryViewModel,
    onOpenLibrary: (LibraryId) -> Unit,
    onAddLibrary: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    recentViewModel: RecentViewModel? = null,
    onOpenDocument: (String) -> Unit = {},
) {
    val state by viewModel.list.collectAsState()
    val recentRows = recentViewModel?.rows?.collectAsState()?.value.orEmpty()
    val recentNotice = recentViewModel?.notice?.collectAsState()?.value
    LibraryHomeContent(
        state = state,
        recentRows = recentRows,
        recentNotice = recentNotice,
        onOpenLibrary = onOpenLibrary,
        onAddLibrary = onAddLibrary,
        onSettings = onSettings,
        onAbout = onAbout,
        onOpenDocument = onOpenDocument,
        onDismissNotice = { viewModel.dismissNotice() },
        onRequestRemove = { viewModel.requestRemove(it) },
        onDismissRemove = { viewModel.dismissRemove() },
        onConfirmRemove = { viewModel.confirmRemove() },
        onDismissRecentNotice = { recentViewModel?.dismissNotice() },
        onRemoveRecent = { recentViewModel?.removeEntry(io.leaflet.model.DocumentId(it)) },
        onClearRecent = { recentViewModel?.clearAll() },
        onRetryLibraries = { viewModel.retryLibraries() },
    )
}

/** One production content tree: large folders/history never push navigation off the page. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun LibraryHomeContent(
    state: LibraryListUiState,
    recentRows: List<RecentRow>,
    recentNotice: String?,
    onOpenLibrary: (LibraryId) -> Unit,
    onAddLibrary: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onDismissNotice: () -> Unit,
    onRequestRemove: (LibraryId) -> Unit,
    onDismissRemove: () -> Unit,
    onConfirmRemove: () -> Unit,
    onDismissRecentNotice: () -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onRetryLibraries: () -> Unit,
) {
    var confirmingClear by remember { mutableStateOf(false) }
    state.pendingRemoval?.let { pending ->
        AlertDialog(
            onDismissRequest = onDismissRemove,
            title = { Text(stringResource(R.string.library_remove_title)) },
            text = { Text(stringResource(R.string.library_remove_body, pending.displayName)) },
            confirmButton = { TextButton(onClick = onConfirmRemove) { Text(stringResource(R.string.library_remove_confirm)) } },
            dismissButton = { TextButton(onClick = onDismissRemove) { Text(stringResource(R.string.scan_cancel)) } },
        )
    }
    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(R.string.home_clear_title)) },
            text = { Text(stringResource(R.string.home_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingClear = false
                        onClearRecent()
                    },
                    modifier = Modifier.testTag(HomeTags.CLEAR_CONFIRM),
                ) { Text(stringResource(R.string.recent_clear_all)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmingClear = false
                }, modifier = Modifier.testTag(HomeTags.CLEAR_CANCEL)) { Text(stringResource(R.string.scan_cancel)) }
            },
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(HomeTags.LIST),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "brand") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                        Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = onSettings, modifier = Modifier.testTag(HomeTags.TOP_SETTINGS)) {
                        Text(stringResource(R.string.go_settings))
                    }
                }
                OutlinedButton(onClick = onAddLibrary) { Text(stringResource(R.string.library_add)) }
            }
        }
        state.notice?.let { notice ->
            item(key = "notice") { HomeNotice(notice, onDismissNotice) }
        }
        recentRows.firstOrNull()?.let { latest ->
            item(key = "continue") {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.home_continue_title), style = MaterialTheme.typography.labelLarge)
                        Text(latest.entry.titleSnapshot, style = MaterialTheme.typography.titleLarge)
                        Text(recentSubtitle(latest), style = MaterialTheme.typography.bodySmall)
                        val unavailable = latest.health == RecentHealth.GONE || latest.health == RecentHealth.AUTH_LOST
                        if (unavailable) {
                            Text(
                                stringResource(
                                    if (latest.health == RecentHealth.GONE) R.string.recent_gone_hint else R.string.recent_auth_lost_hint,
                                ),
                            )
                        }
                        Button(
                            onClick = { onOpenDocument(latest.entry.documentId.value) },
                            modifier = Modifier.testTag(HomeTags.CONTINUE),
                        ) { Text(stringResource(if (unavailable) R.string.home_open_recovery else R.string.home_continue_action)) }
                    }
                }
            }
        }
        item(key = "folders-title") {
            Text(
                if (state.loading || state.loadFailed) {
                    stringResource(R.string.library_title)
                } else {
                    stringResource(R.string.home_folders_count, state.libraries.size)
                },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (state.loading || state.loadFailed) {
            item(key = "load-state") {
                Column {
                    Text(stringResource(if (state.loadFailed) R.string.home_load_failed else R.string.home_loading))
                    if (state.loadFailed) {
                        OutlinedButton(onClick = onRetryLibraries) { Text(stringResource(R.string.scan_retry)) }
                    }
                }
            }
        } else if (state.libraries.isEmpty()) {
            item(key = "empty") {
                Surface(shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp).testTag(HomeTags.EMPTY),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.home_empty_body), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onAddLibrary) { Text(stringResource(R.string.library_add)) }
                    }
                }
            }
        }
        items(state.libraries, key = { "library:${it.id.value}" }) { library ->
            Surface(shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).testTag(HomeTags.library(library.id.value))
                        .combinedClickable(onClick = { onOpenLibrary(library.id) }, onLongClick = { onRequestRemove(library.id) })
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(library.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            homeScanLabel(state.scanStates[library.id.value] ?: ScanState.IDLE),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        if (recentRows.isNotEmpty() || recentNotice != null) {
            item(key = "history-title") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.recent_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (recentRows.isNotEmpty()) {
                        TextButton(onClick = {
                            confirmingClear = true
                        }, modifier = Modifier.testTag(HomeTags.CLEAR)) { Text(stringResource(R.string.recent_clear_all)) }
                    }
                }
            }
            recentNotice?.let { notice -> item(key = "recent-notice") { HomeNotice(notice, onDismissRecentNotice) } }
            items(recentRows, key = { "recent:${it.entry.documentId.value}" }) { row ->
                RecentReadRow(row, onOpenDocument, onRemoveRecent, Modifier.testTag(HomeTags.recent(row.entry.documentId.value)))
            }
        }
        item(key = "navigation") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.home_files_stay), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onSettings,
                        modifier = Modifier.testTag(HomeTags.SETTINGS),
                    ) { Text(stringResource(R.string.go_settings)) }
                    TextButton(onClick = onAbout) { Text(stringResource(R.string.go_about)) }
                }
            }
        }
    }
}

@Composable
private fun HomeNotice(
    text: String,
    onDismiss: () -> Unit,
) {
    Column {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
    }
}

@Composable
private fun homeScanLabel(state: ScanState): String =
    stringResource(
        when (state) {
            ScanState.SCANNING -> R.string.home_updating
            ScanState.FAILED -> R.string.scan_failed_kept
            ScanState.CANCELLED -> R.string.scan_cancelled_kept
            ScanState.IDLE, ScanState.SUCCEEDED -> R.string.home_browse_folder
        },
    )
