package io.leaflet.reader

/**
 * Touch arbitration between content gestures and the page-turn zones
 * (T08, REQ-0005 FR-5). Pure decision table, JVM-testable; the host wires
 * real gesture detectors to [ContentGesture] and only executes the returned
 * [TurnIntent].
 *
 * Priority rule: links, buttons, the code-copy button and horizontal
 * scrollers (wide tables/code, owned by T09) ALWAYS win over the tap zones;
 * a long press NEVER turns a page (it is reserved for selection/menus).
 * Only an unhandled plain tap inside a zone may turn or toggle the menu.
 */
enum class ContentGesture {
    LINK_TAP,
    BUTTON_TAP,
    CODE_COPY_TAP,
    HORIZONTAL_SCROLL,
    LONG_PRESS,
    PLAIN_TAP,
}

/** What the Reader should do after arbitration (NONE = gesture consumed elsewhere). */
enum class TurnIntent {
    PREV_PAGE,
    NEXT_PAGE,
    TOGGLE_MENU,
    NONE,
}

object GestureArbitration {
    /**
     * Decide the reader action for [gesture] landing in [zone].
     * Any non-plain gesture returns [TurnIntent.NONE] regardless of zone;
     * [ContentGesture.PLAIN_TAP] maps PREV → PREV_PAGE, NEXT → NEXT_PAGE,
     * MENU → TOGGLE_MENU.
     */
    fun decide(
        gesture: ContentGesture,
        zone: TapZone,
    ): TurnIntent {
        if (gesture != ContentGesture.PLAIN_TAP) return TurnIntent.NONE
        return when (zone) {
            TapZone.PREV -> TurnIntent.PREV_PAGE
            TapZone.NEXT -> TurnIntent.NEXT_PAGE
            TapZone.MENU -> TurnIntent.TOGGLE_MENU
        }
    }
}
