package com.gallakk.pdfmanager.ui.compress

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.gallakk.pdfmanager.R
import com.gallakk.pdfmanager.files.UriFiles
import com.gallakk.pdfmanager.pdf.CompressionMode
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

class CompressViewModel(application: Application) : OperationViewModel(application) {
    private val _input = MutableStateFlow<Pair<String, File>?>(null)
    val input: StateFlow<Pair<String, File>?> = _input.asStateFlow()
    private val _mode = MutableStateFlow(CompressionMode.LOSSLESS)
    val mode: StateFlow<CompressionMode> = _mode.asStateFlow()

    fun setMode(mode: CompressionMode) {
        _mode.value = mode
    }

    fun select(uri: Uri) {
        viewModelScope.launch {
            val picked = withContext(Dispatchers.IO) {
                runCatching { UriFiles.displayName(context, uri) to UriFiles.copyToCache(context, uri, workDir) }.getOrNull()
            }
            if (picked == null) showMessage(UiText(R.string.invalid_pdf), UiText(R.string.not_a_pdf)) else _input.value = picked
        }
    }

    fun compress(output: Uri) {
        val (_, file) = _input.value ?: run {
            showMessage(UiText(R.string.app_error), UiText(R.string.compress_select_pdf))
            return
        }
        val mode = _mode.value
        launchOperation(failure = { UiText(R.string.compress_fail, listOf(it)) }) { progress ->
            val temporary = File(workDir, "compressed-${System.nanoTime()}.pdf")
            try {
                val result = PdfManagerService.compressPdf(file, temporary, mode, progress)
                UriFiles.copyFileToUri(context, temporary, output)
                val notice = if (result.isLarger) UiText(R.string.compress_larger_notice) else null
                UiText(R.string.compress_success) to UiText(
                    R.string.compress_success_details,
                    listOf(result.originalSize, result.compressedSize, UriFiles.displayName(context, output)),
                    notice,
                )
            } finally {
                temporary.delete()
            }
        }
    }
}
