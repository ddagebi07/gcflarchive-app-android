package kr.co.gcflarchive.jpgtool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    @Test fun targetBytesParsesUnits() {
        assertEquals(500L * 1024, Settings(targetText = "500").targetBytes)
        assertEquals((1.5 * 1024 * 1024).toLong(), Settings(targetText = "1,5", unit = SizeUnit.MB).targetBytes)
        assertNull(Settings(targetText = "").targetBytes)
        assertNull(Settings(targetText = "0").targetBytes)
        assertNull(Settings(targetText = "2").targetBytes) // under 5 KB
    }

    @Test fun maxSideBlankMeansNoLimit() {
        assertEquals(0, Settings(maxSideText = "").maxSide)
        assertEquals(1920, Settings(maxSideText = "1920").maxSide)
        assertNull(Settings(maxSideText = "10").maxSide)
    }

    @Test fun binarySearchFindsHighestFittingQuality() {
        // Fake encoder: size grows linearly with quality.
        val calls = mutableListOf<Int>()
        val q = SizeSearch.highestQualityFitting(limit = 50_000) { calls += it; it * 1_000L }
        assertEquals(50, q)
        assertTrue("should take few encodes, took ${calls.size}", calls.size <= 8)
    }

    @Test fun binarySearchReturnsNullWhenNothingFits() {
        assertNull(SizeSearch.highestQualityFitting(limit = 100) { 10_000L })
        assertEquals(100, SizeSearch.highestQualityFitting(limit = 1_000_000) { it.toLong() })
    }

    @Test fun downscaleFactorIsBounded() {
        val f = SizeSearch.downscaleFactor(sizeAtMin = 400_000, limit = 100_000)
        assertEquals(0.45f, f, 0.001f) // sqrt(1/4) * 0.9
        assertEquals(0.25f, SizeSearch.downscaleFactor(10_000_000, 1_000), 0f)
        assertEquals(0.9f, SizeSearch.downscaleFactor(100, 100_000), 0f) // capped: always shrink at least 10%
    }

    @Test fun fitFactor() {
        assertEquals(1f, SizeSearch.fitFactor(4000, 3000, 0), 0f)
        assertEquals(1f, SizeSearch.fitFactor(1000, 800, 1920), 0f)
        assertEquals(0.5f, SizeSearch.fitFactor(4000, 3000, 2000), 0f)
        assertEquals(0.5f, SizeSearch.fitFactor(3000, 4000, 2000), 0f)
    }

    @Test fun watermarkBottomRight() {
        // 1000×800 photo, 200×100 logo at 20% width, 5% margin of shorter side (40px).
        val b = WatermarkLayout.place(1000, 800, 200, 100, Anchor.BOTTOM_RIGHT, 20f, 5f)
        assertEquals(Box(left = 1000 - 40 - 200, top = 800 - 40 - 100, width = 200, height = 100), b)
    }

    @Test fun watermarkTopLeftAndCenter() {
        assertEquals(Box(40, 40, 200, 100), WatermarkLayout.place(1000, 800, 200, 100, Anchor.TOP_LEFT, 20f, 5f))
        assertEquals(Box(400, 350, 200, 100), WatermarkLayout.place(1000, 800, 200, 100, Anchor.CENTER, 20f, 5f))
    }

    @Test fun tallLogoNeverOverflowsPhoto() {
        // Very tall logo on a wide photo: height capped to the free area, aspect kept.
        val b = WatermarkLayout.place(1000, 200, 100, 1000, Anchor.BOTTOM_LEFT, 80f, 0f)
        assertEquals(200, b.height)
        assertEquals(20, b.width)
        assertTrue(b.top + b.height <= 200 && b.left + b.width <= 1000)
    }

    @Test fun outputName() {
        assertEquals("IMG_001_small.jpg", ImageProcessor.outputName("IMG_001.JPG"))
        assertEquals("photo_small.jpg", ImageProcessor.outputName(".jpg"))
    }

    @Test fun photoFilter() {
        assertTrue(isPhoto("IMG_1.jpg", "image/jpeg"))
        assertTrue(isPhoto("a.png", "image/png"))
        assertTrue(isPhoto("B.JPEG", null))
        assertTrue(isPhoto("c.heic", "application/octet-stream"))
        assertTrue(!isPhoto("notes.txt", "text/plain"))
        assertTrue(!isPhoto("logo.svg", "image/svg+xml"))
        assertTrue(!isPhoto(".trashed-123-IMG.jpg", "image/jpeg"))
        assertTrue(!isPhoto("video.mp4", null))
    }
}
