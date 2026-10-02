package io.leaflet.reader

import io.leaflet.eink.ReadingLayoutPolicy
import io.leaflet.markdown.ReaderStyle

/**
 * Concrete reading layout for one [ReaderStyle] under one [ReadingLayoutPolicy]
 * (REQ-0007 FR-1–FR-4). Plain numbers, so the host only converts units: sizes in
 * sp and margins/padding in dp. The measure is kept in sp because an em follows
 * the font size, including the user's system font scale.
 */
data class ReadingLayout(
    val fontSizeSp: Float,
    val lineHeightSp: Float,
    /** True when [lineHeightSp] is the real baseline distance; false keeps the legacy additive spacing. */
    val exactLineHeight: Boolean,
    val pageMarginDp: Int,
    /** Longest flowing-text line in sp, or null when the text may use the full width. */
    val maxMeasureSp: Float?,
    val justify: Boolean,
    val hyphenate: Boolean,
    val blockPaddingDp: Int,
)

/** Pure resolver shared by the Reader and the Settings preview, so they cannot drift apart. */
object ReadingLayoutResolver {
    /**
     * Maps the user's steps through the display's [policy]. [style] should already be
     * coerced; the step lookups clamp as well, so an out-of-band step never throws.
     */
    fun resolve(
        style: ReaderStyle,
        policy: ReadingLayoutPolicy,
    ): ReadingLayout {
        val fontSizeSp = policy.baseFontSizeSp * ReaderStyle.fontScaleForStep(style.fontSizeStep)
        return ReadingLayout(
            fontSizeSp = fontSizeSp,
            lineHeightSp = fontSizeSp * policy.lineHeightRatioForStep(style.lineSpacingStep),
            exactLineHeight = policy.exactLineHeight,
            pageMarginDp = policy.pageMarginDpForStep(style.pageMarginStep),
            maxMeasureSp = policy.maxMeasureEm?.let { it * fontSizeSp },
            justify = policy.justifyText,
            hyphenate = policy.hyphenate,
            blockPaddingDp = policy.blockPaddingDp,
        )
    }
}
