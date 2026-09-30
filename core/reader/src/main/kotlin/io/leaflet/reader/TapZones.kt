package io.leaflet.reader

/**
 * Reader tap zones (T08, REQ-0005 FR-3).
 *
 * Minimum subset only: left = previous page, center = toggle menu,
 * right = next page. Default split is three equal thirds of the Reader
 * width; the exact widths are UNVERIFIED (REQ-0005 Open Questions) and a
 * configurable zone editor is explicitly out of scope.
 */
enum class TapZone {
    /** Left third: previous page. */
    PREV,

    /** Center third: show/hide the reading menu. */
    MENU,

    /** Right third: next page. */
    NEXT,
}

/** Maps a horizontal tap position to a [TapZone] (pure, JVM-testable). */
object TapZones {
    /**
     * Zone for a tap at [xPx] inside a host [widthPx] wide.
     * Boundaries belong to the zone on their right (`x < w/3` → PREV,
     * `x < 2w/3` → MENU, else NEXT). A non-positive width yields MENU
     * (no turn can be aimed, so never turn).
     */
    fun zoneFor(
        xPx: Float,
        widthPx: Int,
    ): TapZone {
        if (widthPx <= 0) return TapZone.MENU
        val third = widthPx / 3f
        return when {
            xPx < third -> TapZone.PREV
            xPx < third * 2 -> TapZone.MENU
            else -> TapZone.NEXT
        }
    }

    /**
     * First-run tap-zone onboarding decision (UX-06), kept pure so the
     * "at most once per install" rule is JVM-testable without Compose:
     *
     * - [seen] is the persisted flag; `null` means the value has not been read
     *   yet (or no settings store is wired, e.g. a preview), which never shows
     *   the hint — the overlay must not flash before the stored value arrives.
     * - [dismissed] is the in-session dismissal (the user tapped the hint away
     *   before the write landed).
     *
     * The hint shows only for a definite stored `false` that is not yet
     * dismissed.
     */
    fun shouldShowHint(
        seen: Boolean?,
        dismissed: Boolean,
    ): Boolean = seen == false && !dismissed
}
