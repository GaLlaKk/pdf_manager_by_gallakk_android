package com.gallakk.pdfmanager.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gallakk.pdfmanager.R
import com.gallakk.pdfmanager.files.UriFiles
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Exécute une opération PDF hors du thread principal, avec progression et annulation. */
abstract class OperationViewModel(application: Application) : AndroidViewModel(application) {
    protected val context: Context get() = getApplication()
    protected val workDir: File = UriFiles.workDirectory(application)

    private val _state = MutableStateFlow<OperationState>(OperationState.Idle)
    val state: StateFlow<OperationState> = _state.asStateFlow()
    private var job: Job? = null

    protected fun showMessage(title: UiText, message: UiText) {
        _state.value = OperationState.Finished(title, message)
    }

    protected fun launchOperation(
        failure: (String) -> UiText,
        block: suspend (progress: (Int, Int) -> Unit) -> Pair<UiText, UiText>,
    ) {
        if (job?.isActive == true) return
        _state.value = OperationState.Running(0, 0)
        job = viewModelScope.launch {
            try {
                val (title, message) = withContext(Dispatchers.IO) {
                    block { current, total -> _state.value = OperationState.Running(current, total) }
                }
                _state.value = OperationState.Finished(title, message)
            } catch (e: CancellationException) {
                _state.value = OperationState.Idle
                throw e
            } catch (e: Exception) {
                _state.value = OperationState.Finished(UiText(R.string.app_error), failure(e.message ?: e.toString()))
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun dismiss() {
        _state.value = OperationState.Idle
    }

    override fun onCleared() {
        workDir.deleteRecursively()
    }
}
