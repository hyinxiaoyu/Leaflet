package io.leaflet.storage.prefs

import io.leaflet.model.DisplayMode

/**
 * Reader typography/reading defaults, ranges and coercion (REQ-0007 FR-1–FR-4,
 * FR-7; REQ-0010 FR-3; overlap REQ-0008/reading-mode REQ-0005 entry points).
 *
 * Pure Kotlin (no DataStore import) so the rules are JVM-testable; the
 * DataStore glue lives in `:app`'s `SettingsStore`, which MUST use these
 * constants as its single source of defaults. T09 wires the UI to these
 * keys — this file only stores and retrieves, it drives no rendering.
 *
 * Initial values are recorded here (several are still UNVERIFIED pending T09
 * spikes / T13 baselines, noted per field):
 *
 * - Font size: 5 steps (`0..4`), default step `2` (middle). Step count
 *   satisfies REQ-0007 FR-1 ("at least 5 distinguishable steps").
 * - Line spacing: 3 steps (`0..2`), default `1`. (FR-3)
 * - Page margin: 3 steps (`0..2`), default `1`. (FR-4)
 * - Font family: `"system"` sentinel = platform default font. Enumeration
 *   strategy (family query vs fixed candidates) is UNVERIFIED, pending the
 *   T09 spike (REQ-0007 open question); no bundled fonts, system fonts only.
 * - Code soft-wrap: default `true`. REQ-0007 mandates ON for E-Ink; the
 *   Standard default is UNVERIFIED (allowed to differ) — T09 freezes the
 *   per-mode default and this stored value remains the user override.
 * - Page-turn overlap: `0.05` (5%), allowed `0.03..0.08` per REQ-0008/T08.
 * - Reading mode: `PAGE_TURN` default. Page-turn behavior and any
 *   E-Ink-preferred default are owned by T08 (REQ-0005); stored here only.
 * - Tap-zone hint seen: `false` on a fresh install. The reader shows its
 *   one-time tap-zone onboarding while this is `false` and persists `true` on
 *   dismissal (UX-06, `TapZones.shouldShowHint`).
 */
object ReaderSettings {
    const val FONT_SIZE_MIN = 0
    const val FONT_SIZE_MAX = 4
    const val FONT_SIZE_DEFAULT = 2

    const val LINE_SPACING_MIN = 0
    const val LINE_SPACING_MAX = 2
    const val LINE_SPACING_DEFAULT = 1

    const val PAGE_MARGIN_MIN = 0
    const val PAGE_MARGIN_MAX = 2
    const val PAGE_MARGIN_DEFAULT = 1

    const val FONT_FAMILY_DEFAULT = "system"

    const val CODE_SOFT_WRAP_DEFAULT = true

    const val PAGE_OVERLAP_MIN = 0.03f
    const val PAGE_OVERLAP_MAX = 0.08f
    const val PAGE_OVERLAP_DEFAULT = 0.05f

    const val READING_MODE_CONTINUOUS = "CONTINUOUS_SCROLL"
    const val READING_MODE_PAGE_TURN = "PAGE_TURN"
    const val READING_MODE_DEFAULT = READING_MODE_PAGE_TURN

    /**
     * First-run tap-zone onboarding (UX-06): `false` until the reader hint is
     * dismissed once, so a fresh install sees it and every later open does not.
     */
    const val TAP_ZONES_HINT_SEEN_DEFAULT = false

    fun coerceFontSize(step: Int): Int = step.coerceIn(FONT_SIZE_MIN, FONT_SIZE_MAX)

    fun coerceLineSpacing(step: Int): Int = step.coerceIn(LINE_SPACING_MIN, LINE_SPACING_MAX)

    fun coercePageMargin(step: Int): Int = step.coerceIn(PAGE_MARGIN_MIN, PAGE_MARGIN_MAX)

    fun sanitizeFontFamily(family: String): String = if (family.isBlank()) FONT_FAMILY_DEFAULT else family

    fun coercePageOverlap(overlap: Float): Float = overlap.coerceIn(PAGE_OVERLAP_MIN, PAGE_OVERLAP_MAX)

    /**
     * First-run reading mode for display [mode] (REQ-0005 FR-4;
     * 2026-10-01 ebook interaction follow-up): both display modes start in page
     * turns. This is only the value used while the user has never chosen a
     * reading mode — an explicitly stored choice always wins, including after a
     * display-mode switch, because the reading mode is an independent setting
     * (mode switches preserve position, they do not rewrite preferences).
     */
    fun defaultReadingModeFor(mode: DisplayMode): String =
        when (mode) {
            DisplayMode.EINK -> READING_MODE_PAGE_TURN
            DisplayMode.STANDARD -> READING_MODE_PAGE_TURN
        }

    fun sanitizeReadingMode(mode: String): String =
        when (mode) {
            READING_MODE_CONTINUOUS, READING_MODE_PAGE_TURN -> mode
            else -> READING_MODE_DEFAULT
        }
}
