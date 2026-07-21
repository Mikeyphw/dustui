package com.mikeyphw.dustui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.dustui.data.ScanEntryEntity
import com.mikeyphw.dustui.data.ScanSessionEntity
import com.mikeyphw.dustui.scan.ScanStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DustUiApp(viewModel: MainViewModel) {
    DustTheme {
        val rootState by viewModel.rootState.collectAsStateWithLifecycle()
        val session by viewModel.latestSession.collectAsStateWithLifecycle()
        val entries by viewModel.entries.collectAsStateWithLifecycle()
        val message by viewModel.message.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        var customPath by remember { mutableStateOf("/storage/emulated/0") }
        val scanRunning = session?.status in setOf(ScanStatus.STARTING.name, ScanStatus.RUNNING.name)
        val controlsEnabled = rootState == RootUiState.Available && !scanRunning

        LaunchedEffect(message) {
            message?.let {
                snackbar.showSnackbar(it)
                viewModel.clearMessage()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("DustUI Root") },
                    actions = {
                        RootBadge(rootState)
                        Spacer(Modifier.width(12.dp))
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                item { RootAccessCard(rootState, viewModel::refreshRoot) }

                item {
                    Text("Scan presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(viewModel.presets, key = { it.id }) { preset ->
                            Card(
                                onClick = { viewModel.startPreset(preset) },
                                enabled = controlsEnabled,
                                modifier = Modifier.width(220.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(preset.title, fontWeight = FontWeight.SemiBold)
                                    Text(preset.description, style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(8.dp))
                                    Text(preset.path, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Custom root", fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = customPath,
                                onValueChange = { customPath = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Absolute path") },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            )
                            Button(
                                onClick = { viewModel.startCustom(customPath) },
                                enabled = controlsEnabled,
                            ) { Text("Scan with root") }
                        }
                    }
                }

                session?.let { active ->
                    item { SessionCard(active, viewModel::cancelActive) }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Largest entries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text("${entries.size} shown", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    items(entries, key = { "${it.scanId}:${it.path}" }) { entry -> EntryRow(entry, active.totalBytes) }
                } ?: item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                        Text("Choose a preset to begin the first root scan.")
                    }
                }
            }
        }
    }
}

@Composable
private fun RootAccessCard(state: RootUiState, retry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Root access", fontWeight = FontWeight.SemiBold)
                Text(
                    when (state) {
                        RootUiState.Checking -> "Waiting for the root provider"
                        RootUiState.Available -> "Privileged scanner service is ready"
                        RootUiState.Unavailable -> "Grant root access to run protected storage scans"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state == RootUiState.Unavailable) OutlinedButton(onClick = retry) { Text("Retry") }
        }
    }
}

@Composable
private fun RootBadge(state: RootUiState) {
    val (text, color) = when (state) {
        RootUiState.Checking -> "Checking root" to MaterialTheme.colorScheme.secondaryContainer
        RootUiState.Available -> "Root ready" to MaterialTheme.colorScheme.primaryContainer
        RootUiState.Unavailable -> "No root" to MaterialTheme.colorScheme.errorContainer
    }
    Card(colors = CardDefaults.cardColors(containerColor = color)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SessionCard(session: ScanSessionEntity, cancel: () -> Unit) {
    val running = session.status in setOf(ScanStatus.STARTING.name, ScanStatus.RUNNING.name)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(session.rootPath, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(session.status.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelMedium)
                }
                if (running) OutlinedButton(onClick = cancel) { Text("Cancel") }
            }
            if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${session.scannedItems} items")
                Text(formatBytes(session.scannedBytes))
                if (session.totalBytes > 0) Text("Total ${formatBytes(session.totalBytes)}")
            }
            if (session.currentPath.isNotBlank()) {
                Text(session.currentPath, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            session.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun EntryRow(entry: ScanEntryEntity, totalBytes: Long) {
    val share = if (totalBytes > 0) (entry.sizeBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(entry.path, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatBytes(entry.sizeBytes), fontWeight = FontWeight.SemiBold)
                Text(if (entry.isDirectory) "Directory" else "File", style = MaterialTheme.typography.labelSmall)
            }
        }
        LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth())
        Text("Modified ${formatTimestamp(entry.modifiedAt)} · ${entry.fileCount} files", style = MaterialTheme.typography.labelSmall)
    }
}
