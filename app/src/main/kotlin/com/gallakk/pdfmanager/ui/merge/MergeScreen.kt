package com.gallakk.pdfmanager.ui.merge

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
fun MergeScreen(modifier: Modifier = Modifier, viewModel: MergeViewModel = viewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.addUris(uris)
    }
    val createOutput = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) viewModel.merge(uri)
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.merge_title), style = MaterialTheme.typography.titleMedium)
        Button(onClick = { pickPdfs.launch(arrayOf("application/pdf")) }) { Text(stringResource(R.string.open_pdf)) }

        if (items.isEmpty()) {
            Text(stringResource(R.string.no_selection))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                Column(Modifier.fillMaxWidth()) {
                    Text("${index + 1}. ${item.name}")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { viewModel.move(index, -1) }, enabled = index > 0) {
                            Text(stringResource(R.string.move_up))
                        }
                        TextButton(onClick = { viewModel.move(index, 1) }, enabled = index < items.lastIndex) {
                            Text(stringResource(R.string.move_down))
                        }
                        TextButton(onClick = { viewModel.remove(index) }) { Text(stringResource(R.string.remove_selection)) }
                    }
                }
            }
        }

        OperationPanel(state, onCancel = viewModel::cancel, onDismiss = viewModel::dismiss)
        Button(
            onClick = { createOutput.launch("merged.pdf") },
            enabled = items.isNotEmpty() && state !is OperationState.Running,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.merge)) }
    }
}
