package com.gallakk.pdfmanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

enum class Screen(val labelRes: Int) {
    Merge(R.string.merge_tab),
    Split(R.string.split_tab),
    Compress(R.string.compress_tab),
}

@Composable
fun PdfManagerApp() {
    MaterialTheme {
        var current by rememberSaveable { mutableStateOf(Screen.Merge) }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = screen == current,
                            onClick = { current = screen },
                            icon = {},
                            label = { Text(stringResource(screen.labelRes)) },
                        )
                    }
                }
            },
        ) { padding ->
            // Écrans réels ajoutés dans les PR suivantes.
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(current.labelRes))
            }
        }
    }
}
