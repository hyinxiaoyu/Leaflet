package io.leaflet.eink

/**
 * Global motion kill-switch (REQ-0004 FR-2): every animation, transition,
 * crossfade, shimmer or infinite-loading code path MUST consult this policy
 * before running. There is no bypass: under the EINK preset
 * [animationsAllowed] is false and animated loading indicators do not exist
 * (static text + percentage / thin progress bar instead).
 */
data class MotionPolicy(val animationsAllowed: Boolean) {
    companion object {
        fun from(profile: DisplayProfile): MotionPolicy = MotionPolicy(animationsAllowed = profile.animationsEnabled)
    }
}

/**
 * T12 motion gate: wrap any animated value selection with this instead of
 * branching on the mode at the call site. Returns [einkValue] when
 * [animationsAllowed] is false, [standardValue] otherwise.
 */
fun <T> MotionPolicy.pick(
    einkValue: T,
    standardValue: T,
): T = if (animationsAllowed) standardValue else einkValue

/**
 * T12 Compose motion gate: resolves the visible ripple/indication decision
 * from the policy. Callers pass the already-resolved [MotionPolicy] — never
 * the mode — so motion routing stays in one place.
 */
fun MotionPolicy.indicationOn(): Boolean = animationsAllowed

/**
 * Feature availability, decided by build flavor + runtime environment
 * (REQ-0004 FR-5). Answers "can it be used", never "how does it look" —
 * that is [DisplayProfile]'s job. Never scatter raw Booleans or device-brand
 * checks in UI; gate on these capabilities instead.
 */
data class AppCapabilities(
    val supportsStandardMode: Boolean,
    val supportsAnimations: Boolean,
    val supportsDynamicColor: Boolean,
    val supportsOnyxIntegration: Boolean,
    val supportsAdvancedSharing: Boolean,
) {
    companion object {
        /** `full` flavor capabilities (M0): no Onyx integration anywhere yet. */
        fun full() =
            AppCapabilities(
                supportsStandardMode = true,
                supportsAnimations = true,
                supportsDynamicColor = false,
                supportsOnyxIntegration = false,
                supportsAdvancedSharing = true,
            )

        /** `eink` flavor capabilities (M0): trimmed Standard extras, no Onyx yet. */
        fun eink() =
            AppCapabilities(
                supportsStandardMode = true,
                supportsAnimations = false,
                supportsDynamicColor = false,
                supportsOnyxIntegration = false,
                supportsAdvancedSharing = false,
            )
    }
}

/**
 * T12 flavor seam: resolve capabilities from a flavor name ("full"/"eink");
 * unknown names fall back to full. Keeps the AppContainer switch in one
 * place and JVM-testable without BuildConfig.
 */
fun capabilitiesForFlavor(flavorName: String): AppCapabilities =
    if (flavorName == "eink") AppCapabilities.eink() else AppCapabilities.full()
