package com.gallakk.pdfmanager.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.gallakk.pdfmanager.R

class UiText(@StringRes val res: Int, val args: List<Any> = emptyList(), val then: UiText? = null) {
    @Composable
    fun resolve(): String {
        val text = stringResource(res, *args.toTypedArray())
        return then?.let { text + "\n" + it.resolve() } ?: text
    }
}

sealed interface OperationState {
    data object Idle : OperationState
    data class Running(val current: Int, val total: Int) : OperationState
    data class Finished(val title: UiText, val message: UiText) : OperationState
}

@Composable
fun OperationPanel(state: OperationState, onCancel: () -> Unit, onDismiss: () -> Unit) {
    when (state) {
        OperationState.Idle -> Unit
        is OperationState.Running -> Column {
            if (state.total > 0) {
                LinearProgressIndicator(progress = { state.current.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
        is OperationState.Finished -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(state.title.resolve()) },
            text = { Text(state.message.resolve()) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}
