package io.leaflet.reader.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.leaflet.model.LibraryId
import io.leaflet.reader.R
import io.leaflet.storage.ScanState
import io.leaflet.storage.scan.ScanProgressState

/**
 * Library list: empty state → rows (REQ-0001 UX-1/UX-2).
 *
 * The "add" button delegates to [onAddLibrary], which the host Activity
 * binds to `ACTION_OPEN_DOCUMENT_TREE`; the resulting tree URI returns via
 * [LibraryViewModel.onTreePicked]. Static status text per row; no animation.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LibraryListScreen(
    viewModel: LibraryViewModel,
    onOpenLibrary: (LibraryId) -> Unit,
    onAddLibrary: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    recentViewModel: io.leaflet.reader.ui.reader.RecentViewModel? = null,
    onOpenDocument: (String) -> Unit = {},
) {
    val state by viewModel.list.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        if (state.libraries.isEmpty()) {
            Text(stringResource(R.string.library_empty_body))
            Spacer(Modifier.height(16.dp))
        }
        Button(onClick = onAddLibrary) {
            Text(stringResource(R.string.library_add))
        }
        state.notice?.let {
            Spacer(Modifier.height(8.dp))
            Text(it)
            TextButton(onClick = { viewModel.dismissNotice() }) {
                Text(stringResource(R.string.dismiss))
            }
        }
        state.pendingRemoval?.let { pending ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissRemove() },
                title = { Text(stringResource(R.string.library_remove_title)) },
                text = { Text(stringResource(R.string.library_remove_body, pending.displayName)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmRemove() }) {
                        Text(stringResource(R.string.library_remove_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissRemove() }) {
                        Text(stringResource(R.string.scan_cancel))
                    }
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(state.libraries, key = { it.id.value }) { lib ->
                val scan = state.scanStates[lib.id.value] ?: ScanState.IDLE
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .combinedClickable(
                                onClick = { onOpenLibrary(lib.id) },
                                onLongClick = { viewModel.requestRemove(lib.id) },
                            )
                            .padding(vertical = 8.dp),
                ) {
                    Text(lib.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(scanLabel(scan), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (recentViewModel != null) {
            io.leaflet.reader.ui.reader.RecentSection(
                viewModel = recentViewModel,
                onOpenDocument = onOpenDocument,
            )
            Spacer(Modifier.height(8.dp))
        }
        TextButton(onClick = onSettings) { Text(stringResource(R.string.go_settings)) }
        TextButton(onClick = onAbout) { Text(stringResource(R.string.go_about)) }
    }
}

/**
 * Single-library browse: one directory level at a time, plus manual refresh
 * with static throttled progress and cancel (REQ-0001 A1/UX-5).
 *
 * Document rows call [onOpenDocument] with the document id string. This
 * screen never parses content.
 */
@Composable
fun LibraryBrowseScreen(
    viewModel: LibraryViewModel,
    libraryId: LibraryId,
    onOpenDocument: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.browse.collectAsState()
    BackHandler(enabled = state.prefix.isNotEmpty()) {
        viewModel.leaveDirectory()
    }
    val rows = browseLevel(state.documents, state.prefix, state.directories)
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            if (state.displayName.isEmpty()) stringResource(R.string.library_browse_title) else state.displayName,
            style = MaterialTheme.typography.headlineMedium,
        )
        if (state.prefix.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(state.prefix, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        val progress = state.progress
        when (progress.state) {
            ScanProgressState.SCANNING, ScanProgressState.CANCELLING -> {
                Text(progressText(progress.scannedFiles, progress.currentDir))
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.cancelRefresh() }) {
                    Text(stringResource(R.string.scan_cancel))
                }
            }
            ScanProgressState.FAILED -> {
                Text(progress.message ?: stringResource(R.string.scan_failed_kept))
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.refresh(libraryId) }) {
                    Text(stringResource(R.string.scan_retry))
                }
            }
            ScanProgressState.CANCELLED -> {
                Text(progress.message ?: stringResource(R.string.scan_cancelled_kept))
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.refresh(libraryId) }) {
                    Text(stringResource(R.string.scan_refresh))
                }
            }
            ScanProgressState.SUCCEEDED, ScanProgressState.IDLE -> {
                Button(onClick = { viewModel.refresh(libraryId) }) {
                    Text(stringResource(R.string.scan_refresh))
                }
            }
        }
        state.notice?.let {
            Spacer(Modifier.height(8.dp))
            Text(it)
            TextButton(onClick = { viewModel.dismissNotice() }) {
                Text(stringResource(R.string.dismiss))
            }
        }
        Spacer(Modifier.height(16.dp))
        if (rows.isEmpty()) {
            Text(stringResource(R.string.library_browse_empty))
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(rows, key = { it.key }) { entry ->
                    when (entry) {
                        is BrowseLevelRow.Directory ->
                            Text(
                                stringResource(R.string.library_directory_label, entry.name),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .clickable { viewModel.enterDirectory(entry.name) }
                                        .padding(vertical = 8.dp),
                            )
                        is BrowseLevelRow.File ->
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .clickable { onOpenDocument(entry.document.id.value) }
                                        .padding(vertical = 4.dp),
                            ) {
                                Text(entry.document.displayName, style = MaterialTheme.typography.bodyMedium)
                            }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {
                if (!viewModel.leaveDirectory()) onBack()
            },
        ) { Text(stringResource(R.string.go_back)) }
    }
}

private fun scanLabel(state: ScanState): String =
    when (state) {
        ScanState.IDLE -> "空闲"
        ScanState.SCANNING -> "扫描中"
        ScanState.SUCCEEDED -> "扫描完成"
        ScanState.FAILED -> "扫描未完成，已保留上次完整结果"
        ScanState.CANCELLED -> "已取消，显示上次完整结果"
    }

private fun progressText(
    scanned: Int,
    dir: String,
): String = if (dir.isEmpty()) "已扫描 $scanned 项" else "已扫描 $scanned 项 · $dir"
