package kr.co.gcflarchive.jpgtool

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

/** Runs the real Bitmap pipeline (Robolectric native graphics: actual JPEG encoding). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ProcessorTest {
    private val context get() = RuntimeEnvironment.getApplication()

    /** A noisy photo-like image, which compresses badly, like real photos. */
    private fun noisyPhoto(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val rnd = Random(1)
        val px = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            Color.rgb((x * 255 / w + rnd.nextInt(60)) % 256, (y * 255 / h + rnd.nextInt(60)) % 256, rnd.nextInt(256))
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun jpegFile(bmp: Bitmap, quality: Int = 95): File {
        val f = File.createTempFile("in", ".jpg", context.cacheDir)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        return f
    }

    @Test fun compressHitsTargetByQuality() {
        val photo = noisyPhoto(1200, 900)
        val full = ByteArrayOutputStream().also { photo.compress(Bitmap.CompressFormat.JPEG, 100, it) }.size()
        val target = full / 3L
        val out = ImageProcessor.compress(photo, target)
        assertTrue("fits", out.fits)
        assertTrue("${out.bytes.size} <= $target", out.bytes.size <= target)
        assertEquals(1200, out.width) // quality alone was enough
        assertTrue(out.quality!! in SizeSearch.MIN_QUALITY until 100)
        assertTrue(BitmapFactory.decodeByteArray(out.bytes, 0, out.bytes.size) != null)
    }

    @Test fun compressDownscalesWhenQualityIsNotEnough() {
        val out = ImageProcessor.compress(noisyPhoto(2000, 1500), 20L * 1024)
        assertTrue(out.fits)
        assertTrue(out.bytes.size <= 20 * 1024)
        assertTrue("was downscaled: ${out.width}", out.width < 2000)
        assertEquals(4f / 3f, out.width.toFloat() / out.height, 0.02f)
    }

    @Test fun processWithWatermarkAndMaxSide() {
        val input = jpegFile(noisyPhoto(1600, 1200))
        val logo = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawRect(0f, 0f, 200f, 100f, Paint().apply { color = Color.RED })
        }
        val s = Settings(targetText = "300", maxSideText = "800", watermark = true, anchor = Anchor.TOP_LEFT,
            logoPercent = 25f, opacity = 1f, marginPercent = 0f)
        val img = InputImage(Uri.fromFile(input), "a.jpg", input.length(), "image/jpeg")
        val out = ImageProcessor.process(context, img, s, logo)
        assertEquals(800, out.width)
        assertEquals(600, out.height)
        assertTrue(out.bytes.size <= 300 * 1024)
        val decoded = BitmapFactory.decodeByteArray(out.bytes, 0, out.bytes.size)
        val p = decoded.getPixel(20, 20) // inside the logo: red
        assertTrue("logo pixel ${Integer.toHexString(p)}", Color.red(p) > 200 && Color.green(p) < 80 && Color.blue(p) < 80)
    }

    @Test fun halfOpacityBlends() {
        val photo = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val logo = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        ImageProcessor.applyWatermark(photo, logo, Settings(anchor = Anchor.BOTTOM_RIGHT, logoPercent = 25f, opacity = 0.5f, marginPercent = 0f))
        val inside = photo.getPixel(350, 350)
        assertEquals(128f, Color.red(inside).toFloat(), 3f)
        assertEquals(Color.WHITE, photo.getPixel(10, 10))
    }

    @Test fun smallJpegKeptAsIs() {
        val input = jpegFile(noisyPhoto(200, 150), quality = 70)
        val img = InputImage(Uri.fromFile(input), "small.jpg", input.length(), "image/jpeg")
        val out = ImageProcessor.process(context, img, Settings(targetText = "500"), null)
        assertNull(out.quality)
        assertTrue(out.bytes.contentEquals(input.readBytes()))
    }
}
