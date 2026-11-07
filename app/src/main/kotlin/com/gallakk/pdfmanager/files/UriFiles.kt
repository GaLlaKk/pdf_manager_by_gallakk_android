package com.gallakk.pdfmanager.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Pont entre le Storage Access Framework (Uri) et la couche pdf/ (File dans le cache). */
object UriFiles {

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.let { name -> return name }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
    }

    /** Copie le contenu de [uri] dans [directory] (la source n'est jamais modifiée). */
    fun copyToCache(context: Context, uri: Uri, directory: File): File {
        val name = sanitize(displayName(context, uri)).let { if (it.lowercase().endsWith(".pdf")) it else "$it.pdf" }
        val unique = File(directory, java.util.UUID.randomUUID().toString()).apply { mkdirs() }
        val file = File(unique, name)
        val input = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        input.use { source -> file.outputStream().use { source.copyTo(it) } }
        return file
    }

    fun copyFileToUri(context: Context, file: File, uri: Uri) {
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException(uri.toString())
        output.use { sink -> file.inputStream().use { it.copyTo(sink) } }
    }

    /** Crée `name` dans le dossier choisi via ACTION_OPEN_DOCUMENT_TREE et y copie [file]. */
    fun copyFileToTree(context: Context, file: File, treeUri: Uri, name: String): Uri {
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val created = DocumentsContract.createDocument(context.contentResolver, parent, "application/pdf", name)
            ?: throw FileNotFoundException(name)
        copyFileToUri(context, file, created)
        return created
    }

    fun workDirectory(context: Context): File =
        File(context.cacheDir, "work/${java.util.UUID.randomUUID()}").apply { mkdirs() }

    fun sanitize(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
}
