package io.leaflet.reader.ui.reader

import androidx.compose.foundation.gestures.ScrollScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderMenuMotionTest {
    @Test
    fun conservativeMenuReleaseNeverScrollsInEitherDirection() =
        runBlocking {
            var scrollCalls = 0
            val scroll =
                object : ScrollScope {
                    override fun scrollBy(pixels: Float): Float {
                        scrollCalls++
                        return pixels
                    }
                }
            for (velocity in listOf(-10_000f, 0f, 10_000f)) {
                val remaining = with(ReaderMenuNoFling) { scroll.performFling(velocity) }
                assertEquals(velocity, remaining, 0f)
            }
            assertEquals("E-Ink menu must stop on release without frame-driven scroll", 0, scrollCalls)
        }
}
