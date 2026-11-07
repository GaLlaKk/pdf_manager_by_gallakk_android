package com.gallakk.pdfmanager.pdf

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDFormContentStream
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
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
    private fun createImagePdf(
        name: String,
        pixels: Int,
        displayWidth: Float = 144f,
        displayHeight: Float = 144f,
        additionalPlacements: List<Pair<Float, Float>> = emptyList(),
    ): File {
        val file = File(tmp.root, name)
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle(300f, 300f))
            doc.addPage(page)
            val raw = ByteArray(pixels * pixels * 3).also { java.util.Random(1).nextBytes(it) }
            // Le flux est fourni déjà encodé en Flate.
            val data = java.io.ByteArrayOutputStream().also { out ->
                java.util.zip.DeflaterOutputStream(out).use { it.write(raw) }
            }.toByteArray()
            val image = PDImageXObject(
                doc, ByteArrayInputStream(data), COSName.FLATE_DECODE, pixels, pixels, 8, PDDeviceRGB.INSTANCE,
            )
            PDPageContentStream(doc, page).use { stream ->
                stream.drawImage(image, 10f, 10f, displayWidth, displayHeight)
                additionalPlacements.forEachIndexed { index, (width, height) ->
                    stream.drawImage(image, 10f + (index + 1) * 90f, 10f, width, height)
                }
            }
            doc.save(file)
        }
        return file
    }

    private fun createFormImagePdf(name: String, pixels: Int): File {
        val file = File(tmp.root, name)
        PDDocument().use { doc ->
            val imageData = ByteArray(pixels * pixels * 3).also { java.util.Random(1).nextBytes(it) }
            val compressedData = java.io.ByteArrayOutputStream().also { output ->
                java.util.zip.DeflaterOutputStream(output).use { it.write(imageData) }
            }.toByteArray()
            val image = PDImageXObject(
                doc, ByteArrayInputStream(compressedData), COSName.FLATE_DECODE, pixels, pixels, 8,
                PDDeviceRGB.INSTANCE,
            )
            val form = PDFormXObject(doc).apply {
                bBox = PDRectangle(300f, 300f)
                resources = PDResources()
            }
            PDFormContentStream(form).use { it.drawImage(image, 10f, 10f, 144f, 144f) }
            val page = PDPage(PDRectangle(300f, 300f))
            doc.addPage(page)
            PDPageContentStream(doc, page).use { it.drawForm(form) }
            doc.save(file)
        }
        return file
    }

    private fun createMultiPageSharedImagePdf(name: String, pixels: Int): File {
        val file = File(tmp.root, name)
        PDDocument().use { doc ->
            val raw = ByteArray(pixels * pixels * 3).also { java.util.Random(1).nextBytes(it) }
            val data = java.io.ByteArrayOutputStream().also { output ->
                java.util.zip.DeflaterOutputStream(output).use { it.write(raw) }
            }.toByteArray()
            val image = PDImageXObject(
                doc, ByteArrayInputStream(data), COSName.FLATE_DECODE, pixels, pixels, 8, PDDeviceRGB.INSTANCE,
            )
            listOf(72f, 144f).forEach { size ->
                val page = PDPage(PDRectangle(300f, 300f))
                doc.addPage(page)
                PDPageContentStream(doc, page).use { it.drawImage(image, 10f, 10f, size, size) }
            }
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
    fun balancedPreservesImageWhenOneAxisIsBelowThreshold() = runBlocking {
        val pdf = createImagePdf("img.pdf", 600, displayWidth = 144f, displayHeight = 288f)
        val out = File(tmp.root, "balanced.pdf")
        PdfManagerService.compressPdf(pdf, out, CompressionMode.BALANCED)
        assertEquals(600, firstImageWidth(out))
    }

    @Test
    fun balancedUsesLowestResolutionAcrossSharedImagePlacements() = runBlocking {
        val pdf = createImagePdf(
            "img.pdf", 600, displayWidth = 72f, displayHeight = 72f,
            additionalPlacements = listOf(144f to 144f),
        )
        val out = File(tmp.root, "balanced.pdf")
        PdfManagerService.compressPdf(pdf, out, CompressionMode.BALANCED)
        assertEquals(300, firstImageWidth(out))
    }

    @Test
    fun balancedUsesLowestResolutionAcrossPages() = runBlocking {
        val pdf = createMultiPageSharedImagePdf("img.pdf", 600)
        val out = File(tmp.root, "balanced.pdf")
        PdfManagerService.compressPdf(pdf, out, CompressionMode.BALANCED)
        PDDocument.load(out).use { doc ->
            doc.pages.forEach { page ->
                val image = page.resources
                    .getXObject(page.resources.xObjectNames.first()) as PDImageXObject
                assertEquals(300, image.width)
            }
        }
    }

    @Test
    fun balancedRecompressesImagesInFormResources() = runBlocking {
        val pdf = createFormImagePdf("form.pdf", 600)
        val out = File(tmp.root, "balanced.pdf")
        PdfManagerService.compressPdf(pdf, out, CompressionMode.BALANCED)
        PDDocument.load(out).use { doc ->
            val form = doc.getPage(0).resources
                .getXObject(doc.getPage(0).resources.xObjectNames.first()) as PDFormXObject
            val image = form.resources
                .getXObject(form.resources.xObjectNames.first()) as PDImageXObject
            assertEquals(300, image.width)
        }
    }

    @Test
    fun recompressorSkipsBitonalAndColorMaskedImages() {
        val pdf = createImagePdf("img.pdf", 600)
        PDDocument.load(pdf).use { doc ->
            val image = doc.getPage(0).resources
                .getXObject(doc.getPage(0).resources.xObjectNames.first()) as PDImageXObject
            image.cosObject.setInt(COSName.BITS_PER_COMPONENT, 1)
            assertFalse(ImageRecompressor.isCandidate(image, 300f))
            image.cosObject.setInt(COSName.BITS_PER_COMPONENT, 8)
            image.cosObject.setItem(COSName.MASK, COSArray())
            assertFalse(ImageRecompressor.isCandidate(image, 300f))
        }
    }

    @Test
    fun compressionRunsProgressCallbacksOffCallingThread() = runBlocking {
        val pdf = createVerbosePdf("doc.pdf")
        val callerThread = Thread.currentThread()
        var progressThread: Thread? = null
        PdfManagerService.compressPdf(
            pdf, File(tmp.root, "compressed.pdf"), onProgress = { _, _ ->
                progressThread = Thread.currentThread()
            },
        )
        assertTrue(progressThread !== callerThread)
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
