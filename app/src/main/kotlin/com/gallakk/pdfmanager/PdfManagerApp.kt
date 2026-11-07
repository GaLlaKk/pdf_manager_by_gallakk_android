package com.gallakk.pdfmanager

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.gallakk.pdfmanager.ui.compress.CompressScreen
import com.gallakk.pdfmanager.ui.merge.MergeScreen
import com.gallakk.pdfmanager.ui.split.SplitScreen

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
            val content = Modifier.padding(padding)
            when (current) {
                Screen.Merge -> MergeScreen(content)
                Screen.Split -> SplitScreen(content)
                Screen.Compress -> CompressScreen(content)
            }
        }
    }
}
