package io.leaflet.eink

import android.view.View

/**
 * Vendor-neutral E-Ink intent interface (ADR-0005, REQ-0004 FR-6).
 *
 * These are *intent* callbacks — "the reader entered / turned a page" — not
 * hardware commands. Reader code calls them; a platform adapter decides what
 * (if anything) the system does about it.
 */
interface EInkController {
    fun onReaderEnter(view: View?)

    fun onReaderExit(view: View?)

    fun onPageTurn(view: View?)

    fun onScrollStart(view: View?)

    fun onScrollEnd(view: View?)

    fun requestPartialRefresh(view: View?)

    fun requestFullRefresh(view: View?)
}

/**
 * No-op implementation: records nothing, requests nothing.
 * Used on the Standard path and in tests.
 */
class NoOpEInkController : EInkController {
    override fun onReaderEnter(view: View?) = Unit

    override fun onReaderExit(view: View?) = Unit

    override fun onPageTurn(view: View?) = Unit

    override fun onScrollStart(view: View?) = Unit

    override fun onScrollEnd(view: View?) = Unit

    override fun requestPartialRefresh(view: View?) = Unit

    override fun requestFullRefresh(view: View?) = Unit
}

/**
 * Generic system-semantics implementation (ADR-0005 §2).
 *
 * Uses only framework means: instant invalidation via [View.invalidate] and a
 * conservative full-redraw rhythm counter (every [fullRefreshEveryNthIntent]-th
 * page-turn/partial intent also requests one full-redraw pass).
 *
 * HARDWARE DISCLAIMER: this NEVER drives EPD waveforms. `requestFullRefresh`
 * means "ask the Android view system for one full redraw" — it does NOT
 * trigger REGAL/GU partial or GC full hardware refresh, and no Onyx SDK is
 * referenced. Ghosting conclusions come only from the E-Ink device matrix
 * (T13), never from this class.
 */
class GenericEInkController(
    private val fullRefreshEveryNthIntent: Int = 12,
) : EInkController {
    /** Page-turn/partial intents seen since the last full-redraw request. */
    var intentsSinceFullRefresh: Int = 0
        private set

    override fun onReaderEnter(view: View?) {
        view?.invalidate()
    }

    override fun onReaderExit(view: View?) {
        view?.invalidate()
    }

    override fun onPageTurn(view: View?) {
        recordIntent(view)
    }

    override fun onScrollStart(view: View?) = Unit

    override fun onScrollEnd(view: View?) {
        view?.invalidate()
    }

    override fun requestPartialRefresh(view: View?) {
        recordIntent(view)
    }

    override fun requestFullRefresh(view: View?) {
        intentsSinceFullRefresh = 0
        view?.invalidate()
    }

    private fun recordIntent(view: View?) {
        intentsSinceFullRefresh++
        if (intentsSinceFullRefresh >= fullRefreshEveryNthIntent) {
            requestFullRefresh(view)
        } else {
            view?.invalidate()
        }
    }
}

/**
 * T12 controller seam: EINK resolves to [GenericEInkController] (system
 * refresh semantics only — never hardware waveforms), STANDARD to
 * [NoOpEInkController]. Single place so Theme/Reader/Container wiring
 * cannot pick different controllers for the same mode.
 */
fun controllerForMode(mode: DisplayModeId): EInkController =
    when (mode) {
        DisplayModeId.EINK -> GenericEInkController()
        DisplayModeId.STANDARD -> NoOpEInkController()
    }
