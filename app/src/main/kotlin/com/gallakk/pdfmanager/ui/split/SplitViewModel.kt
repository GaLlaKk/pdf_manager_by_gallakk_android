package com.gallakk.pdfmanager.ui.split

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.gallakk.pdfmanager.R
import com.gallakk.pdfmanager.files.UriFiles
import com.gallakk.pdfmanager.pdf.PdfManagerService
import com.gallakk.pdfmanager.ui.OperationViewModel
import com.gallakk.pdfmanager.ui.UiText
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplitViewModel(application: Application) : OperationViewModel(application) {
    private val _input = MutableStateFlow<Pair<String, File>?>(null)
    val input: StateFlow<Pair<String, File>?> = _input.asStateFlow()

    fun select(uri: Uri) {
        viewModelScope.launch {
            val picked = withContext(Dispatchers.IO) {
                runCatching { UriFiles.displayName(context, uri) to UriFiles.copyToCache(context, uri, workDir) }.getOrNull()
            }
            if (picked == null) showMessage(UiText(R.string.invalid_pdf), UiText(R.string.not_a_pdf)) else _input.value = picked
        }
    }

    fun split(treeUri: Uri) {
        val (_, file) = _input.value ?: run {
            showMessage(UiText(R.string.app_error), UiText(R.string.select_pdf_to_split))
            return
        }
        launchOperation(failure = { UiText(R.string.split_fail, listOf(it)) }) { progress ->
            val staging = File(workDir, "split-${System.nanoTime()}").apply { mkdirs() }
            try {
                val files = PdfManagerService.splitPdfPages(file, staging) { current, total -> progress(current, total * 2) }
                files.forEachIndexed { index, part ->
                    UriFiles.copyFileToTree(context, part, treeUri, part.name)
                    progress(files.size + index + 1, files.size * 2)
                }
                val folder = Uri.decode(treeUri.lastPathSegment.orEmpty())
                UiText(R.string.split_success) to UiText(R.string.split_success_details, listOf(files.size, folder))
            } finally {
                staging.deleteRecursively()
            }
        }
    }
}
