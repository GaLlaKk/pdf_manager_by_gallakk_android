package com.gallakk.pdfmanager.ui.split

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gallakk.pdfmanager.R
import com.gallakk.pdfmanager.ui.OperationPanel
import com.gallakk.pdfmanager.ui.OperationState

@Composable
fun SplitScreen(modifier: Modifier = Modifier, viewModel: SplitViewModel = viewModel()) {
    val input by viewModel.input.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.select(uri)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.split(uri)
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.split_title), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { pickPdf.launch(arrayOf("application/pdf")) }) {
            Text(stringResource(R.string.select_pdf))
        }
        Text(input?.first?.let { stringResource(R.string.status_selected_pdf, it) } ?: stringResource(R.string.status_no_pdf))
        Text(stringResource(R.string.split_help), style = MaterialTheme.typography.bodySmall)

        OperationPanel(state, onCancel = viewModel::cancel, onDismiss = viewModel::dismiss)
        Button(
            onClick = { pickFolder.launch(null) },
            enabled = input != null && state !is OperationState.Running,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.split_button)) }
    }
}
