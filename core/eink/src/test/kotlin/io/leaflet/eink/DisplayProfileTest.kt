package io.leaflet.eink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the E-Ink forced off-list (REQ-0004 FR-4) and the Generic controller's
 * full-redraw rhythm against silent drift.
 */
class DisplayProfileTest {
    @Test
    fun einkPresetDisablesEverythingAnimated() {
        val profile = EinkDisplayProfile()

        assertFalse(profile.animationsEnabled)
        assertFalse(profile.smoothScrollingEnabled)
        // Ripple is OFF by default; "extremely weak" needs a T12 definition, so OFF it is.
        assertFalse(profile.rippleEnabled)
        assertTrue(profile.highContrastEnabled)
        assertEquals(ScrollBehavior.PAGE_TURN, profile.scrollBehavior)
        assertEquals(RefreshPolicy.CONSERVATIVE_FULL_REDRAW, profile.refreshPolicy)
    }

    @Test
    fun standardPresetKeepsBasicMotion() {
        val profile = StandardDisplayProfile()

        assertTrue(profile.animationsEnabled)
        assertTrue(profile.smoothScrollingEnabled)
        assertTrue(profile.rippleEnabled)
        assertEquals(ScrollBehavior.CONTINUOUS_SCROLL, profile.scrollBehavior)
    }

    @Test
    fun motionPolicyFollowsProfile() {
        assertFalse(MotionPolicy.from(EinkDisplayProfile()).animationsAllowed)
        assertTrue(MotionPolicy.from(StandardDisplayProfile()).animationsAllowed)
    }

    @Test
    fun capabilitiesStayDecoupledFromProfile() {
        // eink flavor keeps Standard *mode* available (plan §2) while trimming extras.
        assertTrue(AppCapabilities.eink().supportsStandardMode)
        assertFalse(AppCapabilities.eink().supportsAnimations)
        assertFalse(AppCapabilities.eink().supportsOnyxIntegration)
        assertFalse(AppCapabilities.full().supportsOnyxIntegration)
    }

    @Test
    fun genericControllerRequestsFullRedrawOnRhythm() {
        val controller = GenericEInkController(fullRefreshEveryNthIntent = 3)

        controller.onPageTurn(null)
        controller.onPageTurn(null)
        assertEquals(2, controller.intentsSinceFullRefresh)
        controller.onPageTurn(null)

        // Third intent also issued a full redraw, resetting the counter.
        assertEquals(0, controller.intentsSinceFullRefresh)
    }

    // ---- T12 display-strategy seams (REQ-0004 FR-1/FR-2/FR-5, REQ-0010 FR-1/FR-2) ----

    @Test
    fun profileForResolvesBothModesWithoutDrift() {
        val eink = profileFor(DisplayModeId.EINK)
        val standard = profileFor(DisplayModeId.STANDARD)
        assertFalse(eink.animationsEnabled)
        assertFalse(eink.smoothScrollingEnabled)
        assertFalse(eink.rippleEnabled)
        assertEquals(ScrollBehavior.PAGE_TURN, eink.scrollBehavior)
        assertTrue(standard.animationsEnabled)
        assertTrue(standard.smoothScrollingEnabled)
        assertTrue(standard.rippleEnabled)
        assertEquals(ScrollBehavior.CONTINUOUS_SCROLL, standard.scrollBehavior)
    }

    @Test
    fun profileForNameFallsBackToStandard() {
        assertTrue(profileForName("EINK") is EinkDisplayProfile)
        assertTrue(profileForName("STANDARD") is StandardDisplayProfile)
        assertTrue(profileForName("") is StandardDisplayProfile)
        assertTrue(profileForName("bogus") is StandardDisplayProfile)
    }

    @Test
    fun motionPolicyPickRoutesOnPolicyNotMode() {
        val einkPolicy = MotionPolicy.from(EinkDisplayProfile())
        val standardPolicy = MotionPolicy.from(StandardDisplayProfile())
        assertEquals("static", einkPolicy.pick("static", "animated"))
        assertEquals("animated", standardPolicy.pick("static", "animated"))
        assertFalse(einkPolicy.indicationOn())
        assertTrue(standardPolicy.indicationOn())
    }

    @Test
    fun controllerForModeKeepsLifecyclePairing() {
        assertTrue(controllerForMode(DisplayModeId.EINK) is GenericEInkController)
        assertTrue(controllerForMode(DisplayModeId.STANDARD) is NoOpEInkController)
    }

    @Test
    fun capabilitiesForFlavorDefaultsStandardModesBothFlavors() {
        assertTrue(capabilitiesForFlavor("full").supportsStandardMode)
        assertTrue(capabilitiesForFlavor("eink").supportsStandardMode)
        assertEquals(false, capabilitiesForFlavor("eink").supportsOnyxIntegration)
        assertEquals(false, capabilitiesForFlavor("full").supportsOnyxIntegration)
        assertEquals(true, capabilitiesForFlavor("").supportsAdvancedSharing)
    }

    @Test
    fun einkDecorationsStayStaticAndInstant() {
        val decorations = EinkDisplayProfile().readerDecorations
        assertTrue(decorations.minimalStaticChrome)
        assertTrue(decorations.instantMenuVisibility)
    }
}
