package io.leaflet.eink

/**
 * Reading typography and page-layout policy for one [DisplayProfile]
 * (REQ-0004 FR-1, REQ-0007 FR-1–FR-4).
 *
 * The Reader keeps ONE layout path for both flavors and both runtime modes; the
 * only thing that differs is the numbers in this object. Persisted user steps
 * (font size / line spacing / page margin) stay the user's choice — the policy
 * decides what each step means on this display. It is pure Kotlin so every value
 * is JVM-testable, and it never names a device or a vendor.
 *
 * E-Ink numbers are first-principles defaults, NOT device measurements: panel
 * contrast, ghosting and physical legibility remain unverified until the real
 * 6/7/10.3-inch device matrix (REQ-0004 Test Plan, T13) has been run.
 */
data class ReadingLayoutPolicy(
    /** Body font size in sp at step scale 1.0; the Reader multiplies it by the font-size step scale. */
    val baseFontSizeSp: Float,
    /** Line height divided by font size for each line-spacing step (tight, default, relaxed). */
    val lineHeightRatios: List<Float>,
    /**
     * True when the ratio is the real baseline-to-baseline distance (TextView `setLineHeight`).
     * False keeps the legacy behavior of ADDING the extra to the font's natural line height,
     * which renders about 0.17 em looser than the ratio says.
     */
    val exactLineHeight: Boolean,
    /** Horizontal page margin in dp for each page-margin step (narrow, default, wide). */
    val pageMarginDp: List<Int>,
    /** Longest text line in em (font sizes), or null to use the full width. Applies to flowing text only. */
    val maxMeasureEm: Float?,
    /** Justify flowing Markdown text. Literal TXT/fallback blocks are never justified (shown verbatim). */
    val justifyText: Boolean,
    /** Allow automatic hyphenation. Only meaningful together with [justifyText]; the platform picks the locale rules. */
    val hyphenate: Boolean,
    /** Vertical padding above and below each text/code/table block, in dp. */
    val blockPaddingDp: Int,
) {
    init {
        require(baseFontSizeSp > 0f) { "baseFontSizeSp must be positive, was $baseFontSizeSp" }
        require(lineHeightRatios.size == STEP_COUNT) { "need $STEP_COUNT line-height ratios, got ${lineHeightRatios.size}" }
        require(lineHeightRatios.all { it >= 1f }) { "line height below the font size clips glyphs: $lineHeightRatios" }
        require(pageMarginDp.size == STEP_COUNT) { "need $STEP_COUNT page margins, got ${pageMarginDp.size}" }
        require(pageMarginDp.all { it >= 0 }) { "page margins must not be negative: $pageMarginDp" }
        require(maxMeasureEm == null || maxMeasureEm >= MIN_MEASURE_EM) {
            "maxMeasureEm below $MIN_MEASURE_EM em is unreadable, was $maxMeasureEm"
        }
        require(blockPaddingDp >= 0) { "blockPaddingDp must not be negative, was $blockPaddingDp" }
    }

    /** Line-height ratio for a line-spacing step; out-of-range steps clamp to the band. */
    fun lineHeightRatioForStep(step: Int): Float = lineHeightRatios[step.coerceIn(0, STEP_COUNT - 1)]

    /** Page margin in dp for a page-margin step; out-of-range steps clamp to the band. */
    fun pageMarginDpForStep(step: Int): Int = pageMarginDp[step.coerceIn(0, STEP_COUNT - 1)]

    companion object {
        /** The persisted line-spacing and page-margin bands both have three steps (REQ-0007 FR-3/FR-4). */
        const val STEP_COUNT = 3

        /** Below roughly two dozen em even short words wrap badly; guards a mistyped preset. */
        const val MIN_MEASURE_EM = 20f

        /**
         * Standard Android: the pre-existing numbers, unchanged. 16 sp, line height
         * `22/16 × {1.00, 1.25, 1.50}`, margins 8/16/24 dp, no measure cap, ragged right.
         */
        val STANDARD =
            ReadingLayoutPolicy(
                baseFontSizeSp = 16f,
                lineHeightRatios = listOf(1.375f, 1.71875f, 2.0625f),
                exactLineHeight = false,
                pageMarginDp = listOf(8, 16, 24),
                maxMeasureEm = null,
                justifyText = false,
                hyphenate = false,
                blockPaddingDp = 4,
            )

        /**
         * E-Ink: a larger base size (thin strokes lose legibility on dithered panels), tighter
         * exact line spacing (fewer page turns, still ≥ 1.45 for CJK + Latin), wider margins (bezel
         * shadow, bigger margin tap zones), a ~36 em measure so a 10.3-inch panel does not run
         * 100+ characters per line, and justified text with hyphenation so edges stay even
         * without rivers on narrow panels.
         */
        val EINK =
            ReadingLayoutPolicy(
                baseFontSizeSp = 18f,
                lineHeightRatios = listOf(1.45f, 1.6f, 1.8f),
                exactLineHeight = true,
                pageMarginDp = listOf(16, 28, 44),
                maxMeasureEm = 36f,
                justifyText = true,
                hyphenate = true,
                blockPaddingDp = 6,
            )
    }
}

/**
 * Minimum spacing between visible progress/percentage text updates under this
 * refresh policy (REQ-0004 FR-4 "throttle progress updates", REQ-0005 FR-8:
 * at least one second on E-Ink). Zero means "update whenever the value changes".
 */
val RefreshPolicy.progressMinIntervalMs: Long
    get() =
        when (this) {
            RefreshPolicy.SYSTEM_DEFAULT -> 0L
            RefreshPolicy.CONSERVATIVE_FULL_REDRAW -> 1_000L
        }

/**
 * Leading-edge throttle: how long to wait before showing a new progress value.
 * The first value ([lastShownMs] null) and any value after the interval has
 * passed show immediately; a value inside the interval waits for the remainder,
 * so a burst of changes collapses to one trailing update. A clock that moved
 * backwards never waits longer than one interval.
 */
fun progressUpdateDelayMs(
    nowMs: Long,
    lastShownMs: Long?,
    minIntervalMs: Long,
): Long {
    if (minIntervalMs <= 0L || lastShownMs == null) return 0L
    return (lastShownMs + minIntervalMs - nowMs).coerceIn(0L, minIntervalMs)
}
