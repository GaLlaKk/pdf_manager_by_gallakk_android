package com.gallakk.pdfmanager.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** Rotations temporaires : fichier -> (index de page 0-based -> delta en degrés). */
typealias PageRotations = Map<File, Map<Int, Int>>

/** Progression : (étape courante, total d'étapes). */
typealias ProgressListener = (current: Int, total: Int) -> Unit

private val VALID_ROTATIONS = setOf(0, 90, 180, 270)

/** Équivalent de `pdf_manager_service.py` ; sans dépendance à l'UI Android. */
object PdfManagerService {

    fun isPdfFile(file: File): Boolean =
        file.name.lowercase().endsWith(".pdf") && file.isFile

    fun formatPageNumber(pageNumber: Int, totalPages: Int): String {
        if (totalPages <= 0) return pageNumber.toString()
        return pageNumber.toString().padStart(totalPages.toString().length, '0')
    }

    /** Fusionne [pdfFiles] dans l'ordre de la liste en appliquant [pageRotations]. */
    suspend fun mergePdfs(
        pdfFiles: List<File>,
        output: File,
        pageRotations: PageRotations = emptyMap(),
        onProgress: ProgressListener = { _, _ -> },
    ) {
        require(pdfFiles.isNotEmpty()) { "Aucun fichier PDF sélectionné." }
        val destination = output.absoluteFile
        // Les sources doivent rester ouvertes jusqu'à l'enregistrement.
        val sources = mutableListOf<PDDocument>()
        try {
            writeAtomically(destination, ".pdf-merge-") { temporary ->
                PDDocument(memorySetting()).use { merged ->
                    pdfFiles.forEachIndexed { index, file ->
                        coroutineContext.ensureActive()
                        if (!file.isFile) throw FileNotFoundException("Fichier introuvable : $file")
                        val source = PDDocument.load(file, memorySetting())
                        sources += source
                        val firstPageIndex = merged.numberOfPages
                        for (page in source.pages) merged.importPage(page)
                        applyPageRotations(merged, firstPageIndex, pageRotations[file].orEmpty())
                        onProgress(index + 1, pdfFiles.size)
                    }
                    coroutineContext.ensureActive()
                    merged.save(temporary)
                }
            }
        } finally {
            sources.forEach { runCatching { it.close() } }
        }
    }

    /** Découpe [pdfFile] en `{nom}_{n}.pdf` (n rempli de zéros) dans [outputDir]. */
    suspend fun splitPdfPages(
        pdfFile: File,
        outputDir: File? = null,
        onProgress: ProgressListener = { _, _ -> },
    ): List<File> {
        require(isPdfFile(pdfFile)) { "Le fichier PDF est introuvable ou invalide : $pdfFile" }
        val input = pdfFile.absoluteFile
        val destinationDir = (outputDir ?: input.parentFile!!).absoluteFile
        destinationDir.mkdirs()

        val created = mutableListOf<File>()
        try {
            PDDocument.load(input, memorySetting()).use { source ->
                val total = source.numberOfPages
                require(total > 0) { "Le PDF est vide : $input" }
                val baseName = input.nameWithoutExtension
                source.pages.forEachIndexed { index, page ->
                    coroutineContext.ensureActive()
                    val target = File(destinationDir, "${baseName}_${formatPageNumber(index + 1, total)}.pdf")
                    writeAtomically(target, ".pdf-split-") { temporary ->
                        PDDocument(memorySetting()).use { single ->
                            single.importPage(page)
                            single.save(temporary)
                        }
                    }
                    created += target
                    onProgress(index + 1, total)
                }
            }
        } catch (e: Throwable) {
            // Pas de résultat partiel en cas d'échec ou d'annulation.
            created.forEach { it.delete() }
            throw e
        }
        return created
    }

    /** Enregistre une copie pivotée de [pdfFile] sans modifier la source. */
    suspend fun rotatePdfPages(
        pdfFile: File,
        output: File,
        pageRotations: Map<Int, Int>,
    ): File {
        require(isPdfFile(pdfFile)) { "The PDF file is missing or invalid: $pdfFile" }
        val destination = validateOutput(pdfFile, output)
        writeAtomically(destination, ".pdf-rotate-") { temporary ->
            PDDocument.load(pdfFile, memorySetting()).use { document ->
                applyPageRotations(document, 0, pageRotations)
                coroutineContext.ensureActive()
                document.save(temporary)
            }
        }
        return destination
    }

    internal fun validateOutput(source: File, output: File): File {
        require(output.name.lowercase().endsWith(".pdf")) { "The output path must have a .pdf extension." }
        val destination = output.absoluteFile
        require(source.absoluteFile.canonicalFile != destination.canonicalFile) {
            "The output file must be different from the source file."
        }
        return destination
    }

    internal fun applyPageRotations(document: PDDocument, firstPageIndex: Int, rotations: Map<Int, Int>) {
        val pageCount = document.numberOfPages - firstPageIndex
        // Validation complète avant toute modification.
        for ((pageIndex, rotation) in rotations) {
            require(pageIndex >= 0) { "Invalid PDF page index: $pageIndex" }
            require(pageIndex < pageCount) { "PDF page index out of range: $pageIndex" }
            require(rotation in VALID_ROTATIONS) { "Invalid page rotation: $rotation" }
        }
        for ((pageIndex, rotation) in rotations) {
            if (rotation == 0) continue
            val page = document.getPage(firstPageIndex + pageIndex)
            page.rotation = (page.rotation + rotation).mod(360)
        }
    }

    internal fun memorySetting(): MemoryUsageSetting = MemoryUsageSetting.setupTempFileOnly()

    /** Écrit dans un temporaire du même dossier puis renomme ; supprime le temporaire en cas d'échec. */
    internal inline fun writeAtomically(destination: File, prefix: String, write: (File) -> Unit) {
        val directory = destination.absoluteFile.parentFile!!
        directory.mkdirs()
        val temporary = File.createTempFile(prefix, ".pdf", directory)
        try {
            write(temporary)
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }
}
