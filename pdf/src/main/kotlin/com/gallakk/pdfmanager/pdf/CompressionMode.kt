package com.gallakk.pdfmanager.pdf

enum class CompressionMode(val id: String) {
    /** Optimise la structure sans toucher à la qualité des images. */
    LOSSLESS("lossless"),

    /** En plus, recompresse les images > 200 dpi vers 150 dpi, JPEG qualité 65. */
    BALANCED("balanced");

    companion object {
        const val DPI_THRESHOLD = 200
        const val DPI_TARGET = 150
        const val JPEG_QUALITY = 0.65f

        fun parse(id: String): CompressionMode =
            entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unsupported compression mode: $id")
    }
}

data class CompressionResult(
    val output: java.io.File,
    val originalSize: Long,
    val compressedSize: Long,
) {
    /** Vrai si le PDF compressé est plus gros que l'original (à signaler à l'utilisateur). */
    val isLarger: Boolean get() = compressedSize > originalSize
}
