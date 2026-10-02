package io.leaflet.reader.ui.reader

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.leaflet.eink.pick
import io.leaflet.reader.R
import io.leaflet.reader.ReadingMode
import io.leaflet.reader.ui.theme.LocalMotionPolicy

/** No frame-driven motion after release; let the gesture end with its velocity unconsumed. */
internal object ReaderMenuNoFling : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float = initialVelocity
}

object ReaderControlTags {
    const val PREVIOUS = "reader-previous"
    const val NEXT = "reader-next"
    const val MENU = "reader-menu"
    const val ACTIONS = "reader-actions"
    const val HELP = "reader-help"
}

/** Visible alternatives to hidden touch zones; all callbacks use the existing reader state. */
@Composable
internal fun ReaderNavigationBar(
    mode: ReadingMode,
    progress: String,
    menuVisible: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        if (mode == ReadingMode.PAGE_TURN) {
            TextButton(
                onClick = onPrevious,
                enabled = canPrevious,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(ReaderControlTags.PREVIOUS),
            ) {
                Text(stringResource(R.string.reader_previous_screen), textAlign = TextAlign.Center)
            }
        }
        TextButton(
            onClick = onMenu,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(ReaderControlTags.MENU),
        ) {
            Column {
                Text(progress, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(if (menuVisible) R.string.reader_close_menu else R.string.reader_open_menu),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (mode == ReadingMode.PAGE_TURN) {
            TextButton(
                onClick = onNext,
                enabled = canNext,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(ReaderControlTags.NEXT),
            ) {
                Text(stringResource(R.string.reader_next_screen), textAlign = TextAlign.Center)
            }
        }
    }
}

/** Wrap first, then scroll within a viewport-relative cap so controls cannot fill the body. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderActionMenu(
    mode: ReadingMode,
    maxHeight: Dp,
    canChangeMode: Boolean,
    onToc: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onMode: () -> Unit,
    onHelp: () -> Unit,
    onBack: () -> Unit,
) {
    val flingBehavior =
        LocalMotionPolicy.current.pick(
            einkValue = ReaderMenuNoFling,
            standardValue = ScrollableDefaults.flingBehavior(),
        )
    FlowRow(
        modifier =
            Modifier.fillMaxWidth().heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState(), flingBehavior = flingBehavior).padding(horizontal = 4.dp)
                .testTag(ReaderControlTags.ACTIONS),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        TextButton(onClick = onToc, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.reader_toc)) }
        TextButton(onClick = onSearch, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.reader_search)) }
        TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.reader_refresh)) }
        if (canChangeMode) {
            TextButton(onClick = onMode, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (mode == ReadingMode.PAGE_TURN) R.string.reader_mode_continuous else R.string.reader_mode_page_turn))
            }
        }
        TextButton(onClick = onHelp, modifier = Modifier.heightIn(min = 48.dp).testTag(ReaderControlTags.HELP)) {
            Text(stringResource(R.string.reader_controls_help))
        }
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.go_back)) }
    }
}
