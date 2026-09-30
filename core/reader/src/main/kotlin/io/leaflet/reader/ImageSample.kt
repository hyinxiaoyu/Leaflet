package io.leaflet.reader

/**
 * Viewport-sampled image decode math (T10, REQ-0009 FR-6).
 *
 * Pure JVM-testable arithmetic. Platform decoding (`BitmapFactory` bounds
 * pass → `inSampleSize` → sampled decode, one fresh stream per pass) lives
 * in `:app`; every pass there closes its stream via `use {}` and checks
 * cancellation between passes, so scrolling away or closing the document
 * releases the work instead of leaking it.
 */
object ImageSample {
    /** Decoded bytes per pixel (`Bitmap.Config.ARGB_8888`, the decode default). */
    const val BYTES_PER_PIXEL = 4

    /**
     * Single-image decode ceiling.
     *
     * UNVERIFIED placeholder pending the REQ-0011 memory baseline (REQ-0009
     * Open Questions): this number only asserts "bounded +
     * downsample-or-placeholder, never OOM". Do NOT treat it as a measured
     * budget.
     */
    const val MAX_DECODE_BYTES: Long = 12L * 1024L * 1024L

    /**
     * Largest power-of-two downsample keeping the decoded width at or above
     * the viewport width. Unknown dimensions or viewport → 1 (attempt, then
     * let the result placeholders on failure).
     */
    fun inSampleSizeForWidth(
        srcWidth: Int,
        srcHeight: Int,
        reqWidthPx: Int,
    ): Int {
        if (srcWidth <= 0 || srcHeight <= 0 || reqWidthPx <= 0) return 1
        var sample = 1
        while (srcWidth / (sample * 2) >= reqWidthPx) {
            sample *= 2
        }
        return sample
    }

    /** Estimated decoded bytes for [srcWidth]×[srcHeight] at [sample] (ceiling division). */
    fun decodedBytes(
        srcWidth: Int,
        srcHeight: Int,
        sample: Int,
    ): Long {
        val s = sample.coerceAtLeast(1)
        val width = ((srcWidth + s - 1) / s).coerceAtLeast(1).toLong()
        val height = ((srcHeight + s - 1) / s).coerceAtLeast(1).toLong()
        return width * height * BYTES_PER_PIXEL
    }

    /**
     * Full sampling decision: viewport width first, then grow the sample
     * until the estimate fits [MAX_DECODE_BYTES]. Oversized images
     * downsample instead of throwing OOM; the loop always terminates
     * (estimates shrink toward one pixel).
     */
    fun sampleFor(
        srcWidth: Int,
        srcHeight: Int,
        reqWidthPx: Int,
    ): Int {
        var sample = inSampleSizeForWidth(srcWidth, srcHeight, reqWidthPx)
        var guard = 0
        while (decodedBytes(srcWidth, srcHeight, sample) > MAX_DECODE_BYTES && guard < 24) {
            sample *= 2
            guard++
        }
        return sample
    }

    /** True when the sampled decode fits the ceiling (else the caller shows a placeholder). */
    fun fitsBudget(
        srcWidth: Int,
        srcHeight: Int,
        sample: Int,
    ): Boolean = decodedBytes(srcWidth, srcHeight, sample) <= MAX_DECODE_BYTES
}
