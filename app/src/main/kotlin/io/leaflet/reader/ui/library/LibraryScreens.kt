package io.leaflet.reader.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.leaflet.model.LibraryId
import io.leaflet.reader.R
import io.leaflet.storage.ScanState
import io.leaflet.storage.scan.ScanProgress
import io.leaflet.storage.scan.ScanProgressState

/**
 * Stable test tags for the browse screen (REQ-0012, NB-02/NB-03/NB-04).
 * Production and the instrumented page tests share them so an assertion
 * cannot silently drift from the composable it checks.
 */
object BrowseTags {
    const val LIST = "browse-list"
    const val SCAN_STATUS = "browse-scan-status"
    const val SCAN_ACTION = "browse-scan-action"

    fun breadcrumb(target: String): String = "browse-breadcrumb:$target"

    fun row(key: String): String = "browse-row:$key"
}

/**
 * Single-library browse: one directory level at a time, a breadcrumb with a
 * validated ancestor action, a reserved scan-status region, and typed rows
 * (REQ-0012; REQ-0001 A1/UX-5).
 *
 * Document rows call [onOpenDocument] with the document id string. This
 * screen never parses content and never queries SAF for navigation.
 */
@Composable
fun LibraryBrowseScreen(
    viewModel: LibraryViewModel,
    libraryId: LibraryId,
    onOpenDocument: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.browse.collectAsState()
    // Capture values while rendering: reading the delegated State inside a queued
    // callback would attach the latest Library/prefix to an older breadcrumb tap.
    val renderedLibraryId = state.libraryId
    val renderedPrefix = state.prefix
    BackHandler(enabled = state.prefix.isNotEmpty()) {
        viewModel.leaveDirectory()
    }
    val rows = browseLevel(state.documents, state.prefix, state.directories)
    val listState = rememberLazyListState()
    // A different Library or directory always starts its list at the top
    // (FR-6/AC-6). Tapping the current breadcrumb entry is a ViewModel no-op, so
    // it does not change the key and never re-runs this effect.
    LaunchedEffect(state.libraryId, state.prefix) { listState.scrollToItem(0) }
    val rootLabel =
        if (state.displayName.isEmpty()) {
            stringResource(R.string.library_breadcrumb_root)
        } else {
            state.displayName
        }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            if (state.displayName.isEmpty()) stringResource(R.string.library_browse_title) else state.displayName,
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(4.dp))
        BreadcrumbRow(
            segments = breadcrumbSegments(renderedPrefix, rootLabel),
            onSelect = { target ->
                // Provenance travels with the tap (NAV-ACC-01): the entry was
                // rendered for this Library and prefix, and the ViewModel
                // verifies both are still current before moving.
                viewModel.openAncestor(
                    AncestorRequest(target = target, libraryId = renderedLibraryId, prefix = renderedPrefix),
                )
            },
        )
        Spacer(Modifier.height(8.dp))
        ScanStatusRegion(
            progress = state.progress,
            onRefresh = { viewModel.refresh(libraryId) },
            onCancel = { viewModel.cancelRefresh() },
        )
        Spacer(Modifier.height(16.dp))
        if (rows.isEmpty()) {
            Text(stringResource(R.string.library_browse_empty))
        } else {
            // Bounded viewport (REQ-0012 FR-3/FR-4): without a weight the list
            // would report its full content height inside this Column, push the
            // Back control off-screen, and never scroll — so the "list
            // viewport" the scan-status checks measure would not exist.
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f).testTag(BrowseTags.LIST)) {
                items(rows, key = { it.key }) { entry ->
                    BrowseRow(
                        entry = entry,
                        onEnterDirectory = { viewModel.enterDirectory(it) },
                        onOpenDocument = onOpenDocument,
                    )
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

/**
 * Breadcrumb (REQ-0012 FR-3, AC-1/AC-3): the Library root plus every ancestor
 * of the open directory in ONE bounded-height row that scrolls horizontally,
 * so a ten-level path stays reachable on a narrow screen. Each entry is at
 * least 48 dp; the current entry is bold, carries a state description, and is
 * not clickable, so it offers no redundant navigation action.
 */
@Composable
private fun BreadcrumbRow(
    segments: List<BreadcrumbSegment>,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, segment ->
            if (index > 0) {
                Text(
                    text = stringResource(R.string.library_breadcrumb_separator),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            BreadcrumbEntry(segment = segment, onSelect = onSelect)
        }
    }
}

@Composable
private fun BreadcrumbEntry(
    segment: BreadcrumbSegment,
    onSelect: (String) -> Unit,
) {
    val currentLabel = stringResource(R.string.library_breadcrumb_current)
    if (segment.isCurrent) {
        Text(
            text = segment.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag(BrowseTags.breadcrumb(segment.target))
                    .semantics { stateDescription = currentLabel }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
        )
    } else {
        Text(
            text = segment.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag(BrowseTags.breadcrumb(segment.target))
                    .clickable { onSelect(segment.target) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

/**
 * Reserved scan-status region (REQ-0012 FR-4, AC-4/NB-03). The status line
 * always occupies two lines and exactly one action button is always present,
 * so `IDLE → SCANNING → CANCELLING → CANCELLED`, `SUCCEEDED`, and
 * `FAILED → Retry` cannot change the region's outer size — and therefore
 * cannot move the file list below it. The full failure/cancellation text
 * stays exposed to accessibility even when it is visually ellipsized.
 */
@Composable
private fun ScanStatusRegion(
    progress: ScanProgress,
    onRefresh: () -> Unit,
    onCancel: () -> Unit,
) {
    val failedFallback = stringResource(R.string.scan_failed_kept)
    val cancelledFallback = stringResource(R.string.scan_cancelled_kept)
    val statusText =
        when (progress.state) {
            ScanProgressState.SCANNING, ScanProgressState.CANCELLING ->
                scanProgressText(progress.scannedFiles, progress.currentDir)
            ScanProgressState.FAILED -> progress.message ?: failedFallback
            ScanProgressState.CANCELLED -> progress.message ?: cancelledFallback
            ScanProgressState.IDLE, ScanProgressState.SUCCEEDED -> ""
        }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().testTag(BrowseTags.SCAN_STATUS),
        )
        Spacer(Modifier.height(8.dp))
        val scanning =
            progress.state == ScanProgressState.SCANNING ||
                progress.state == ScanProgressState.CANCELLING
        val actionLabel =
            when {
                scanning -> R.string.scan_cancel
                progress.state == ScanProgressState.FAILED -> R.string.scan_retry
                else -> R.string.scan_refresh
            }
        Button(
            onClick = if (scanning) onCancel else onRefresh,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag(BrowseTags.SCAN_ACTION),
        ) {
            Text(stringResource(actionLabel), maxLines = 1)
        }
    }
}

/**
 * One browse row (REQ-0012 FR-5, AC-5/NB-04): the whole row is clickable and
 * at least 48 dp tall, grows for wrapped names, and pairs a non-color type cue
 * (folder / document icon plus an MD/TXT tag) with the file name. Directory
 * ordering is decided by [browseLevel], not here.
 */
@Composable
private fun BrowseRow(
    entry: BrowseLevelRow,
    onEnterDirectory: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag(BrowseTags.row(entry.key))
                .clickable {
                    when (entry) {
                        is BrowseLevelRow.Directory -> onEnterDirectory(entry.name)
                        is BrowseLevelRow.File -> onOpenDocument(entry.document.id.value)
                    }
                }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (entry) {
            is BrowseLevelRow.Directory -> {
                Icon(
                    painter = painterResource(R.drawable.ic_folder),
                    contentDescription = stringResource(R.string.library_row_folder),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.library_directory_label, entry.name),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is BrowseLevelRow.File -> {
                Icon(
                    painter = painterResource(R.drawable.ic_document),
                    contentDescription = stringResource(R.string.library_row_document),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = entry.document.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.document.extension.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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

@Composable
private fun scanProgressText(
    scanned: Int,
    dir: String,
): String =
    if (dir.isEmpty()) {
        stringResource(R.string.library_scan_progress, scanned)
    } else {
        stringResource(R.string.library_scan_progress_dir, scanned, dir)
    }
