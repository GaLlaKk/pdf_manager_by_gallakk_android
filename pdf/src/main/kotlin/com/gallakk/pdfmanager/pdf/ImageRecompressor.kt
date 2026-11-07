package com.gallakk.pdfmanager.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlin.math.roundToInt

/** Rééchantillonne une image à [CompressionMode.DPI_TARGET] et la réencode en JPEG (mode équilibré). */
internal object ImageRecompressor {

    fun scaleFor(dpi: Float): Float = CompressionMode.DPI_TARGET / dpi

    /** Vrai si l'image doit être recompressée (hors masques/pochoirs, qui perdraient leur transparence). */
    fun isCandidate(image: PDImageXObject, dpi: Float): Boolean =
        dpi > CompressionMode.DPI_THRESHOLD &&
            !image.isStencil &&
            image.mask == null &&
            image.softMask == null

    /** Retourne la nouvelle image, ou null si elle n'est pas plus petite que l'originale. */
    fun recompress(document: PDDocument, image: PDImageXObject, dpi: Float): PDImageXObject? {
        val source = image.image
        var scaled: Bitmap? = null
        try {
            val factor = scaleFor(dpi)
            val width = (source.width * factor).roundToInt().coerceAtLeast(1)
            val height = (source.height * factor).roundToInt().coerceAtLeast(1)
            scaled = Bitmap.createScaledBitmap(source, width, height, true)
            val replacement = JPEGFactory.createFromImage(
                document, scaled, CompressionMode.JPEG_QUALITY, CompressionMode.DPI_TARGET,
            )
            return replacement.takeIf { it.stream.length < image.stream.length }
        } finally {
            if (scaled != null && scaled !== source) scaled.recycle()
            source.recycle()
        }
    }
}
