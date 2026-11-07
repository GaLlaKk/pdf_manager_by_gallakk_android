package com.gallakk.pdfmanager.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfManagerServiceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Page i a une largeur de [baseWidth] + i points : permet d'identifier l'ordre. */
    private fun createPdf(name: String, pages: Int, baseWidth: Int = 100, dir: File = tmp.root): File {
        val file = File(dir, name)
        PDDocument().use { doc ->
            repeat(pages) { doc.addPage(PDPage(PDRectangle((baseWidth + it).toFloat(), 200f))) }
            doc.save(file)
        }
        return file
    }

    private fun widths(file: File): List<Int> =
        PDDocument.load(file).use { d -> d.pages.map { it.mediaBox.width.toInt() } }

    private fun rotations(file: File): List<Int> =
        PDDocument.load(file).use { d -> d.pages.map { it.rotation } }

    private fun names(files: List<File>) = files.map { it.name }

    @Test
    fun splitNamesForSingleDigitCount() = runBlocking {
        val pdf = createPdf("doc.pdf", 3)
        val out = tmp.newFolder("out")
        val files = PdfManagerService.splitPdfPages(pdf, out)
        assertEquals(listOf("doc_1.pdf", "doc_2.pdf", "doc_3.pdf"), names(files))
        files.forEach { assertEquals(1, widths(it).size) }
    }

    @Test
    fun splitNamesForDoubleDigitCount() = runBlocking {
        val pdf = createPdf("doc.pdf", 12)
        val files = PdfManagerService.splitPdfPages(pdf, tmp.newFolder("out"))
        assertEquals("doc_01.pdf", files.first().name)
        assertEquals("doc_12.pdf", files.last().name)
        assertEquals(listOf(105), widths(files[5]))
    }

    @Test
    fun splitNamesForTripleDigitCount() = runBlocking {
        val pdf = createPdf("doc.pdf", 123)
        val files = PdfManagerService.splitPdfPages(pdf, tmp.newFolder("out"))
        assertEquals("doc_001.pdf", files.first().name)
        assertEquals("doc_123.pdf", files.last().name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun splitRejectsMissingFile(): Unit = runBlocking {
        PdfManagerService.splitPdfPages(File(tmp.root, "missing.pdf"))
        Unit
    }

    @Test
    fun mergeRespectsListOrder() = runBlocking {
        val a = createPdf("a.pdf", 2, 100)
        val b = createPdf("b.pdf", 1, 300)
        val out = File(tmp.root, "merged.pdf")
        PdfManagerService.mergePdfs(listOf(b, a), out)
        assertEquals(listOf(300, 100, 101), widths(out))
        assertEquals(listOf(100, 101), widths(a))
    }

    @Test
    fun mergeAppliesPreviewRotationsPerFile() = runBlocking {
        val a = createPdf("a.pdf", 2, 100)
        val b = createPdf("b.pdf", 2, 300)
        val out = File(tmp.root, "merged.pdf")
        PdfManagerService.mergePdfs(listOf(a, b), out, mapOf(a to mapOf(1 to 90), b to mapOf(0 to 270)))
        assertEquals(listOf(0, 90, 270, 0), rotations(out))
        assertEquals(listOf(0, 0), rotations(a))
    }

    @Test
    fun mergeRejectsEmptyListAndMissingFile() {
        val out = File(tmp.root, "merged.pdf")
        assertThrowsIllegal { PdfManagerService.mergePdfs(emptyList(), out) }
        assertThrowsIO { PdfManagerService.mergePdfs(listOf(File(tmp.root, "x.pdf")), out) }
        assertFalse(out.exists())
        assertEquals(emptyList<String>(), tmp.root.list()!!.filter { it.startsWith(".pdf-") })
    }

    @Test
    fun mergeRejectsInvalidRotationAndLeavesNoOutput() {
        val a = createPdf("a.pdf", 1)
        val out = File(tmp.root, "merged.pdf")
        assertThrowsIllegal { PdfManagerService.mergePdfs(listOf(a), out, mapOf(a to mapOf(0 to 45))) }
        assertThrowsIllegal { PdfManagerService.mergePdfs(listOf(a), out, mapOf(a to mapOf(5 to 90))) }
        assertThrowsIllegal { PdfManagerService.mergePdfs(listOf(a), out, mapOf(a to mapOf(-1 to 90))) }
        assertFalse(out.exists())
        assertTrue(tmp.root.list()!!.none { it.startsWith(".pdf-") })
    }

    @Test
    fun rotateSavesCopyWithoutChangingSource() = runBlocking {
        val pdf = createPdf("doc.pdf", 2)
        val before = pdf.readBytes()
        val out = File(tmp.root, "doc_rotated.pdf")
        val result = PdfManagerService.rotatePdfPages(pdf, out, mapOf(0 to 90, 1 to 180))
        assertEquals(out.absoluteFile, result)
        assertEquals(listOf(90, 180), rotations(out))
        assertArrayEquals(before, pdf.readBytes())
    }

    @Test
    fun rotationIsAddedToExistingRotation() = runBlocking {
        val pdf = createPdf("doc.pdf", 1)
        val first = File(tmp.root, "r1.pdf")
        val second = File(tmp.root, "r2.pdf")
        PdfManagerService.rotatePdfPages(pdf, first, mapOf(0 to 270))
        PdfManagerService.rotatePdfPages(first, second, mapOf(0 to 180))
        assertEquals(listOf(90), rotations(second))
    }

    @Test
    fun rotateRejectsOutputEqualToSourceAndBadExtension() {
        val pdf = createPdf("doc.pdf", 1)
        assertThrowsIllegal { PdfManagerService.rotatePdfPages(pdf, pdf, mapOf(0 to 90)) }
        assertThrowsIllegal { PdfManagerService.rotatePdfPages(pdf, File(tmp.root, "x.txt"), mapOf(0 to 90)) }
        assertEquals(listOf(0), rotations(pdf))
    }

    @Test
    fun rotateRejectsInvalidRotation() {
        val pdf = createPdf("doc.pdf", 1)
        val out = File(tmp.root, "out.pdf")
        assertThrowsIllegal { PdfManagerService.rotatePdfPages(pdf, out, mapOf(0 to 45)) }
        assertFalse(out.exists())
    }

    @Test
    fun isPdfFileChecksExtensionAndExistence() {
        val pdf = createPdf("doc.PDF", 1)
        assertTrue(PdfManagerService.isPdfFile(pdf))
        assertFalse(PdfManagerService.isPdfFile(File(tmp.root, "missing.pdf")))
        assertFalse(PdfManagerService.isPdfFile(tmp.newFile("a.txt")))
    }

    private fun assertThrowsIllegal(block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            throw AssertionError("IllegalArgumentException attendue")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun assertThrowsIO(block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            throw AssertionError("FileNotFoundException attendue")
        } catch (_: java.io.FileNotFoundException) {
        }
    }
}
