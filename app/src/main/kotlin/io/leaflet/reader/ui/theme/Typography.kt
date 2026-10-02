package io.leaflet.reader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Chrome typography for the resolved display profile (REQ-0004 FR-1, REQ-0007).
 *
 * Standard keeps Material 3's own scale. The high-contrast (E-Ink) preset lifts the small
 * styles that the library rows, scan status, progress footer, notices and buttons use:
 * 11–14 sp regular-weight strokes lose their thin parts on a dithered panel, so those
 * styles start at 14 sp and use the Medium weight. Reading text is NOT set here — the Reader
 * takes its size, spacing and measure from `ReadingLayoutPolicy`.
 *
 * Numbers are first-principles defaults; physical legibility is unverified until the E-Ink
 * device matrix has been run.
 */
internal fun leafletTypography(highContrast: Boolean): Typography {
    val base = Typography()
    if (!highContrast) return base
    return base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodySmall = base.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelLarge = base.labelLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
        labelMedium = base.labelMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelSmall = base.labelSmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
    )
}
