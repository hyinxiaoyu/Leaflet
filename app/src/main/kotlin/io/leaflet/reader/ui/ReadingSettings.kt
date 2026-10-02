package io.leaflet.reader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.leaflet.eink.AppCapabilities
import io.leaflet.eink.DisplayModeId
import io.leaflet.eink.profileFor
import io.leaflet.markdown.ReaderStyle
import io.leaflet.model.DisplayMode
import io.leaflet.reader.R
import io.leaflet.reader.ReadingLayoutResolver
import io.leaflet.reader.SettingsStore
import io.leaflet.reader.SettingsViewModel
import io.leaflet.storage.prefs.ReaderSettings

object SettingsTags {
    const val PREVIEW = "settings-preview"
    const val PREVIEW_TEXT = "settings-preview-text"
}

/** Existing persisted settings, with the same resolved bands as the production Reader. */
@Composable
fun SettingsScreen(
    store: SettingsStore,
    capabilities: AppCapabilities,
    viewModel: SettingsViewModel,
    onAbout: () -> Unit,
    onBack: () -> Unit,
) {
    val mode by viewModel.displayMode.collectAsState(initial = store.defaultMode)
    val readingMode by viewModel.readingMode.collectAsState(initial = ReaderSettings.defaultReadingModeFor(viewModel.defaultDisplayMode))
    val fontSize by viewModel.fontSizeStep.collectAsState(initial = ReaderSettings.FONT_SIZE_DEFAULT)
    val family by viewModel.fontFamily.collectAsState(initial = ReaderSettings.FONT_FAMILY_DEFAULT)
    val spacing by viewModel.lineSpacingStep.collectAsState(initial = ReaderSettings.LINE_SPACING_DEFAULT)
    val margin by viewModel.pageMarginStep.collectAsState(initial = ReaderSettings.PAGE_MARGIN_DEFAULT)
    val wrapStored by viewModel.codeSoftWrap.collectAsState(initial = null)
    val wrap = viewModel.effectiveCodeSoftWrap(wrapStored, mode == DisplayMode.EINK)
    // The preview resolves through the same display-profile policy as the production Reader
    // (REQ-0007), so what it shows is what the Reader will use on this display. The step labels
    // below stay relative multipliers so they read the same in both modes.
    val layout =
        ReadingLayoutResolver.resolve(
            ReaderStyle(fontSizeStep = fontSize, lineSpacingStep = spacing, pageMarginStep = margin).coerce(),
            profileFor(if (mode == DisplayMode.EINK) DisplayModeId.EINK else DisplayModeId.STANDARD).readingLayout,
        )
    val sizeSp = layout.fontSizeSp
    val lineMultiplier = ReaderStyle.lineSpacingMultiplierForStep(spacing)
    val marginDp = layout.pageMarginDp
    val bodyFamily =
        when (family) {
            "serif" -> FontFamily.Serif
            else -> FontFamily.SansSerif
        }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "header") {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.settings_saved_note), style = MaterialTheme.typography.bodyMedium)
        }
        item(key = "preview") {
            Surface(
                modifier = Modifier.fillMaxWidth().testTag(SettingsTags.PREVIEW),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_preview_title), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.settings_preview_body),
                        fontFamily = bodyFamily,
                        fontSize = sizeSp.sp,
                        lineHeight = layout.lineHeightSp.sp,
                        textAlign = if (layout.justify) TextAlign.Justify else TextAlign.Unspecified,
                        modifier = Modifier.padding(horizontal = marginDp.dp, vertical = 16.dp).testTag(SettingsTags.PREVIEW_TEXT),
                    )
                }
            }
        }
        item(key = "display") {
            SettingsGroup(stringResource(R.string.settings_display_heading)) {
                SettingChoice(
                    stringResource(R.string.mode_standard),
                    mode == DisplayMode.STANDARD,
                ) { viewModel.setDisplayMode(DisplayMode.STANDARD) }
                SettingChoice(stringResource(R.string.mode_eink), mode == DisplayMode.EINK) { viewModel.setDisplayMode(DisplayMode.EINK) }
            }
        }
        item(key = "reading") {
            SettingsGroup(stringResource(R.string.settings_reading_heading)) {
                SettingChoice(stringResource(R.string.reading_mode_continuous), readingMode == ReaderSettings.READING_MODE_CONTINUOUS) {
                    viewModel.setReadingMode(ReaderSettings.READING_MODE_CONTINUOUS)
                }
                SettingChoice(stringResource(R.string.reading_mode_page_turn), readingMode == ReaderSettings.READING_MODE_PAGE_TURN) {
                    viewModel.setReadingMode(ReaderSettings.READING_MODE_PAGE_TURN)
                }
            }
        }
        item(key = "typography") {
            SettingsGroup(stringResource(R.string.settings_typography_heading)) {
                ValueStepper(
                    stringResource(R.string.typography_font_size),
                    stringResource(R.string.settings_font_size_actual, sizeSp),
                    fontSize > 0,
                    fontSize < 4,
                    { viewModel.setFontSizeStep(fontSize - 1) },
                    { viewModel.setFontSizeStep(fontSize + 1) },
                )
                SettingChoice(stringResource(R.string.typography_font_system), family == "system") { viewModel.setFontFamily("system") }
                SettingChoice(
                    stringResource(R.string.typography_font_sans),
                    family == "sans-serif",
                ) { viewModel.setFontFamily("sans-serif") }
                SettingChoice(stringResource(R.string.typography_font_serif), family == "serif") { viewModel.setFontFamily("serif") }
                ValueStepper(
                    stringResource(R.string.typography_line_spacing),
                    stringResource(R.string.settings_spacing_actual, lineMultiplier),
                    spacing > 0,
                    spacing < 2,
                    { viewModel.setLineSpacingStep(spacing - 1) },
                    { viewModel.setLineSpacingStep(spacing + 1) },
                )
                ValueStepper(
                    stringResource(R.string.typography_page_margin),
                    stringResource(R.string.settings_margin_actual, marginDp),
                    margin > 0,
                    margin < 2,
                    { viewModel.setPageMarginStep(margin - 1) },
                    { viewModel.setPageMarginStep(margin + 1) },
                )
                SettingChoice(stringResource(R.string.typography_soft_wrap_on), wrap) { viewModel.setCodeSoftWrap(true) }
                SettingChoice(stringResource(R.string.typography_soft_wrap_off), !wrap) { viewModel.setCodeSoftWrap(false) }
            }
        }
        item(key = "footer") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.go_back)) }
                TextButton(onClick = onAbout) { Text(stringResource(R.string.go_about)) }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

/** Static selected surface: the same target and border weight in both states. */
@Composable
private fun SettingChoice(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                .clickable(role = Role.RadioButton, onClick = onSelect).semantics { selected = isSelected }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (isSelected) Text("✓", fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
private fun ValueStepper(
    label: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val decreaseLabel = stringResource(R.string.settings_decrease, label)
    val increaseLabel = stringResource(R.string.settings_increase, label)
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDecrease, enabled = canDecrease, modifier = Modifier.semantics { contentDescription = decreaseLabel }) {
                Text("−")
            }
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(
                onClick = onIncrease,
                enabled = canIncrease,
                modifier = Modifier.semantics { contentDescription = increaseLabel },
            ) { Text("+") }
        }
    }
}
