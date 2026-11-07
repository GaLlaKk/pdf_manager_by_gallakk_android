package com.gallakk.pdfmanager.ui.compress

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gallakk.pdfmanager.R
import com.gallakk.pdfmanager.pdf.CompressionMode
import com.gallakk.pdfmanager.ui.OperationPanel
import com.gallakk.pdfmanager.ui.OperationState

@Composable
fun CompressScreen(modifier: Modifier = Modifier, viewModel: CompressViewModel = viewModel()) {
    val input by viewModel.input.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.select(uri)
    }
    val createOutput = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) viewModel.compress(uri)
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.compress_title), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { pickPdf.launch(arrayOf("application/pdf")) }) {
            Text(stringResource(R.string.compress_choose_file))
        }
        Text(input?.first?.let { stringResource(R.string.status_selected_pdf, it) } ?: stringResource(R.string.status_no_pdf))

        Text(stringResource(R.string.compress_mode_label))
        ModeOption(CompressionMode.LOSSLESS, R.string.compress_lossless, mode) { viewModel.setMode(it) }
        ModeOption(CompressionMode.BALANCED, R.string.compress_balanced, mode) { viewModel.setMode(it) }
        Text(stringResource(R.string.compress_info), style = MaterialTheme.typography.bodySmall)

        OperationPanel(state, onCancel = viewModel::cancel, onDismiss = viewModel::dismiss)
        Button(
            onClick = {
                val base = input?.first?.substringBeforeLast('.') ?: "document"
                createOutput.launch("${base}_compressed.pdf")
            },
            enabled = input != null && state !is OperationState.Running,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.compress_button)) }
    }
}

@Composable
private fun ModeOption(option: CompressionMode, label: Int, selected: CompressionMode, onSelect: (CompressionMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = option == selected, onClick = null)
        Text(stringResource(label), Modifier.padding(start = 8.dp))
    }
}
