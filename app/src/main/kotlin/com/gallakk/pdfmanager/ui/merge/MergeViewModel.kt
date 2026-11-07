package com.gallakk.pdfmanager.ui.merge

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PdfItem(val id: Long, val name: String, val file: File)

class MergeViewModel(application: Application) : OperationViewModel(application) {
    private val _items = MutableStateFlow<List<PdfItem>>(emptyList())
    val items: StateFlow<List<PdfItem>> = _items.asStateFlow()
    private var nextId = 0L

    fun addUris(uris: List<Uri>) {
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val file = UriFiles.copyToCache(context, uri, workDir)
                        PdfItem(nextId++, UriFiles.displayName(context, uri), file)
                    }.getOrNull()
                }
            }
            _items.update { it + added }
            if (added.size < uris.size) showMessage(UiText(R.string.invalid_pdf), UiText(R.string.not_a_pdf))
        }
    }

    fun remove(index: Int) = _items.update { list -> list.filterIndexed { i, _ -> i != index } }

    fun move(index: Int, delta: Int) = _items.update { list ->
        val target = index + delta
        if (index !in list.indices || target !in list.indices) return@update list
        list.toMutableList().apply { add(target, removeAt(index)) }
    }

    fun merge(output: Uri) {
        val files = _items.value.map { it.file }
        if (files.isEmpty()) {
            showMessage(UiText(R.string.app_error), UiText(R.string.select_pdf_to_merge))
            return
        }
        launchOperation(failure = { UiText(R.string.merge_fail, listOf(it)) }) { progress ->
            val temporary = File(workDir, "merged-${System.nanoTime()}.pdf")
            try {
                PdfManagerService.mergePdfs(files, temporary, onProgress = progress)
                UriFiles.copyFileToUri(context, temporary, output)
            } finally {
                temporary.delete()
            }
            UiText(R.string.success_merge) to UiText(R.string.merge_success_details, listOf(UriFiles.displayName(context, output)))
        }
    }
}
