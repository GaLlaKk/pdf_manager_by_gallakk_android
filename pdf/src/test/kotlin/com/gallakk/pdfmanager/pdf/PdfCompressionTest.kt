package com.gallakk.pdfmanager.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric : PDFBox-Android et Bitmap ont besoin des classes android.graphics.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfCompressionTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** PDF dont les flux de contenu ne sont pas compressés. */
    private fun createVerbosePdf(name: String, pages: Int = 2): File {
        val file = File(tmp.root, name)
        PDDocument().use { doc ->
            repeat(pages) {
                val page = PDPage(PDRectangle(300f, 300f))
                doc.addPage(page)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, false).use { cs ->
                    repeat(2000) { i -> cs.addRect(i % 200f, i % 150f, 10f, 10f) }
                    cs.fill()
                }
            }
            doc.save(file)
        }
        return file
    }

    private fun pageCount(file: File) = PDDocument.load(file).use { it.numberOfPages }

    @Test
    fun losslessPreservesSourceAndPages() = runBlocking {
        val pdf = createVerbosePdf("doc.pdf", 3)
        val before = pdf.readBytes()
        val out = File(tmp.root, "small.pdf")
        val result = PdfManagerService.compressPdf(pdf, out, CompressionMode.LOSSLESS)
        assertEquals(3, pageCount(out))
        assertArrayEquals(before, pdf.readBytes())
        assertEquals(out.absoluteFile, result.output)
        assertEquals(before.size.toLong(), result.originalSize)
        assertEquals(out.length(), result.compressedSize)
        assertTrue(result.compressedSize < result.originalSize)
        assertFalse(result.isLarger)
    }

    @Test
    fun balancedWithoutImagesProducesValidPdf() = runBlocking {
        val pdf = createVerbosePdf("doc.pdf")
        val out = File(tmp.root, "balanced.pdf")
        PdfManagerService.compressPdf(pdf, out, CompressionMode.BALANCED)
        assertEquals(2, pageCount(out))
    }

    @Test
    fun rejectsOverwritingSourceAndBadExtension() {
        val pdf = createVerbosePdf("doc.pdf")
        val before = pdf.readBytes()
        assertThrowsIllegal { PdfManagerService.compressPdf(pdf, pdf) }
        assertThrowsIllegal { PdfManagerService.compressPdf(pdf, File(tmp.root, "out.txt")) }
        assertArrayEquals(before, pdf.readBytes())
    }

    @Test
    fun unknownModeIsRejected() {
        try {
            CompressionMode.parse("extreme")
            throw AssertionError("IllegalArgumentException attendue")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(CompressionMode.BALANCED, CompressionMode.parse("balanced"))
    }

    @Test
    fun invalidSourceLeavesNoOutputNorTemporary() {
        val bad = File(tmp.root, "bad.pdf").apply { writeText("pas un pdf") }
        val out = File(tmp.root, "out.pdf")
        try {
            runBlocking { PdfManagerService.compressPdf(bad, out) }
            throw AssertionError("Échec attendu")
        } catch (_: AssertionError) {
            throw AssertionError("Échec attendu")
        } catch (_: Exception) {
        }
        assertFalse(out.exists())
        assertTrue(tmp.root.list()!!.none { it.startsWith(".pdf-") })
    }

    /** Image RGB de [pixels] px affichée sur 144 pt (2 pouces), soit pixels/2 dpi. */
    private fun createImagePdf(name: String, pixels: Int): File {
        val file = File(tmp.root, name)
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle(300f, 300f))
            doc.addPage(page)
            val data = ByteArray(pixels * pixels * 3).also { java.util.Random(1).nextBytes(it) }
            val image = PDImageXObject(
                doc, ByteArrayInputStream(data), COSName.FLATE_DECODE, pixels, pixels, 8, PDDeviceRGB.INSTANCE,
            )
            PDPageContentStream(doc, page).use { it.drawImage(image, 10f, 10f, 144f, 144f) }
            doc.save(file)
        }
        return file
    }

    private fun firstImageWidth(file: File): Int = PDDocument.load(file).use { doc ->
        val resources = doc.getPage(0).resources
        (resources.getXObject(resources.xObjectNames.first()) as PDImageXObject).width
    }

    @Test
    fun balancedDownsamplesHighResolutionImage() = runBlocking {
        val pdf = createImagePdf("img.pdf", 600)
        val before = pdf.readBytes()
        val out = File(tmp.root, "balanced.pdf")
        val result = PdfManagerService.compressPdf(pdf, out, CompressionMode.BALANCED)
        assertEquals(300, firstImageWidth(out))
        assertTrue(result.compressedSize < result.originalSize)
        assertArrayEquals(before, pdf.readBytes())
    }

    @Test
    fun losslessKeepsImageResolution() = runBlocking {
        val pdf = createImagePdf("img.pdf", 600)
        val out = File(tmp.root, "lossless.pdf")
        PdfManagerService.compressPdf(pdf, out, CompressionMode.LOSSLESS)
        assertEquals(600, firstImageWidth(out))
    }

    @Test
    fun balancedKeepsLowResolutionImage() = runBlocking {
        val pdf = createImagePdf("img.pdf", 300)
        val out = File(tmp.root, "balanced.pdf")
        PdfManagerService.compressPdf(pdf, out, CompressionMode.BALANCED)
        assertEquals(300, firstImageWidth(out))
    }

    @Test
    fun scannerEstimatesEffectiveDpi() {
        val file = createImagePdf("img.pdf", 600)
        PDDocument.load(file).use { doc ->
            val page = doc.getPage(0)
            val scanner = ImageDpiScanner()
            scanner.processPage(page)
            val image = page.resources.getXObject(page.resources.xObjectNames.first()) as PDImageXObject
            assertEquals(300f, scanner.dpiOf(image), 0.5f)
        }
        assertEquals(0.5f, ImageRecompressor.scaleFor(300f), 0.001f)
    }

    private fun assertThrowsIllegal(block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            throw AssertionError("IllegalArgumentException attendue")
        } catch (_: IllegalArgumentException) {
        }
    }
}
