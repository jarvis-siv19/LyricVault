package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ai.AiModel
import com.example.ai.AiSettings
import com.example.ai.GeminiClient
import com.example.ai.ModelCatalog
import com.example.ai.formatBytes
import com.example.ui.viewmodel.AiDownloadState
import com.example.ui.viewmodel.AiUiState
import com.example.ui.viewmodel.KeyTestState
import com.example.ui.viewmodel.LyricVaultViewModel

private const val GEMINI_KEY_URL = "https://aistudio.google.com/apikey"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    viewModel: LyricVaultViewModel,
    onNavigateBack: () -> Unit
) {
    val aiState by viewModel.aiUiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Matching") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "ai_intro") {
                Text(
                    text = "On-device AI finds ideas by meaning, not just shared words — " +
                        "and explains why a match fits. Everything runs on your phone; " +
                        "nothing leaves it unless you choose Gemini below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- Device card ----
            item(key = "device_card") {
                AiSectionCard(title = "Your device") {
                    val specs = aiState.specs
                    if (specs == null) {
                        Text(
                            text = "Could not read device specs.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        AiSpecRow("RAM", "${specs.totalRamLabel} total · ${specs.freeRamLabel} free")
                        AiSpecRow("Chipset", specs.socModel)
                        AiSpecRow("CPU cores", specs.cpuCores.toString())
                        AiSpecRow("Android", "API ${specs.androidVersion} · ${specs.abi}")
                        aiState.tier?.let { tier ->
                            AiSpecRow("Tier", tier.label)
                        }
                        aiState.recommendedLlm?.let { model ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Recommended for your device: ${model.displayName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // ---- Enable switch ----
            item(key = "enable_ai") {
                AiSectionCard(title = "AI matching") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable AI matching",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (aiState.enabled) {
                                    "Vector search is active. Keyword matching still runs alongside it."
                                } else {
                                    "Downloads the small MiniLM embedder (~23 MB) and indexes your vault by meaning."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = aiState.enabled,
                            onCheckedChange = { viewModel.setAiEnabled(it) },
                            modifier = Modifier.testTag("ai_enable_switch")
                        )
                    }
                    if (aiState.enabled) {
                        val progress = aiState.reembedProgress
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { (progress.done.toFloat() / progress.total.coerceAtLeast(1)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Embedding your vault: ${progress.done}/${progress.total}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "${aiState.embeddedCount} of ${aiState.totalIdeas} ideas embedded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = { viewModel.reembedVault() }) {
                                Text("Re-embed vault")
                            }
                        }
                    }
                }
            }

            // ---- Embedder picker ----
            item(key = "embedder_picker") {
                AiSectionCard(title = "Embedder — what finds matches") {
                    Text(
                        text = "The embedder turns your ideas into meaning-vectors. " +
                            "Switching embedders re-indexes your vault (a few seconds).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    for (model in ModelCatalog.embedders) {
                        val selected = aiState.embedderId == model.id
                        AiRadioRow(
                            selected = selected,
                            title = model.displayName,
                            subtitle = model.description + if (model.noDownload) {
                                " — uses your Gemini key, nothing to download."
                            } else {
                                " — ${formatBytes(model.totalSizeBytes)} download."
                            },
                            onClick = { viewModel.setEmbedder(model.id) }
                        )
                        if (!model.noDownload && aiState.enabled) {
                            ModelDownloadRow(
                                model = model,
                                state = aiState.downloads[model.id] ?: AiDownloadState.NotDownloaded,
                                onDownload = { viewModel.downloadModel(model.id) },
                                onPause = { viewModel.pauseDownload(model.id) },
                                onCancel = { viewModel.cancelDownload(model.id) },
                                onDelete = { viewModel.deleteModel(model.id) }
                            )
                        }
                    }
                    if (aiState.embedderId == "gemini" && !aiState.hasGeminiKey) {
                        Text(
                            text = "Add your Gemini API key below to use Gemini embeddings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // ---- LLM picker + downloads ----
            item(key = "llm_picker") {
                AiSectionCard(title = "Explanation model — “Why this fits”") {
                    Text(
                        text = "An LLM writes one short sentence per match explaining why it fits. " +
                            "On-device models run offline; Gemini uses your own key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AiRadioRow(
                        selected = aiState.llmId == AiSettings.LLM_NONE,
                        title = "None",
                        subtitle = "No explanations. Matching still works.",
                        onClick = { viewModel.setLlm(AiSettings.LLM_NONE) }
                    )
                    for (model in ModelCatalog.llms) {
                        val selected = aiState.llmId == model.id
                        val recommended = aiState.recommendedLlm?.id == model.id
                        AiRadioRow(
                            selected = selected,
                            title = model.displayName + if (recommended) " — recommended" else "",
                            subtitle = "${model.description} · ${formatBytes(model.totalSizeBytes)} · ${model.speedNote}",
                            onClick = { viewModel.setLlm(model.id) }
                        )
                        ModelDownloadRow(
                            model = model,
                            state = aiState.downloads[model.id] ?: AiDownloadState.NotDownloaded,
                            onDownload = { viewModel.downloadModel(model.id) },
                            onPause = { viewModel.pauseDownload(model.id) },
                            onCancel = { viewModel.cancelDownload(model.id) },
                            onDelete = { viewModel.deleteModel(model.id) }
                        )
                    }
                    AiRadioRow(
                        selected = aiState.llmId == "gemini",
                        title = "Gemini (your API key)",
                        subtitle = "Uses the Gemini model picked below. No download.",
                        onClick = { viewModel.setLlm("gemini") }
                    )

                    // Backend + network options
                    Text(
                        text = "On-device backend",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AiSettings.BackendPreference.entries.forEachIndexed { index, pref ->
                            SegmentedButton(
                                selected = aiState.backend == pref,
                                onClick = { viewModel.setBackendPreference(pref) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = AiSettings.BackendPreference.entries.size
                                )
                            ) {
                                Text(pref.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                    Text(
                        text = "Auto uses the GPU only for the large model on high-end phones; CPU everywhere else.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Download on Wi-Fi only",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = aiState.wifiOnly,
                            onCheckedChange = { viewModel.setWifiOnlyDownloads(it) }
                        )
                    }
                }
            }

            // ---- Gemini BYOK ----
            item(key = "gemini_byok") {
                GeminiByokSection(
                    aiState = aiState,
                    viewModel = viewModel,
                    onOpenKeyPage = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(GEMINI_KEY_URL))
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AiSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                content()
            }
        )
    }
}

@Composable
private fun AiSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.widthIn(max = 240.dp)
        )
    }
}

@Composable
private fun AiRadioRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModelDownloadRow(
    model: AiModel,
    state: AiDownloadState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (state) {
            is AiDownloadState.NotDownloaded -> {
                OutlinedButton(onClick = onDownload) {
                    Icon(imageVector = Icons.Filled.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Download ${formatBytes(model.totalSizeBytes)}")
                }
            }
            is AiDownloadState.Downloading -> {
                val fraction = if (state.totalBytes > 0) {
                    (state.downloadedBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f)
                } else 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onPause) { Text("Pause") }
                }
            }
            is AiDownloadState.Paused -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Paused — progress is kept.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDownload) {
                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                        Text("Resume")
                    }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
            is AiDownloadState.Installed -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Installed (${formatBytes(model.totalSizeBytes)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete ${model.displayName}"
                        )
                    }
                }
            }
            is AiDownloadState.Failed -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(onClick = onDownload) { Text("Retry download") }
            }
        }
    }
}

@Composable
private fun GeminiByokSection(
    aiState: AiUiState,
    viewModel: LyricVaultViewModel,
    onOpenKeyPage: () -> Unit
) {
    var keyInput by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var customModel by remember { mutableStateOf("") }

    val currentModel = aiState.geminiModel
    val isCustomModel = currentModel != GeminiClient.DEFAULT_LLM_MODEL &&
        currentModel != GeminiClient.LIGHT_LLM_MODEL

    AiSectionCard(title = "Gemini — bring your own key (optional)") {
        Text(
            text = "Use Google's Gemini for embeddings, explanations, or both — " +
                "with your own free API key. Gemini's free tier needs no card. " +
                "Your key stays on this phone, encrypted; queries go to Google only " +
                "when you pick Gemini above.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Numbered setup steps
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "How to get a key",
                style = MaterialTheme.typography.titleSmall
            )
            GeminiStep("1", "Open Google AI Studio's API key page:")
            OutlinedButton(onClick = onOpenKeyPage) {
                Icon(imageVector = Icons.Filled.OpenInBrowser, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("aistudio.google.com/apikey")
            }
            GeminiStep("2", "Sign in with your Google account and tap “Create API key”.")
            GeminiStep("3", "Copy the key (it starts with AIza…) and paste it below.")
            GeminiStep("4", "Tap Save & test. Then pick Gemini as your embedder and/or explanation model above.")
        }

        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text("Gemini API key") },
            placeholder = {
                Text(
                    if (aiState.hasGeminiKey) "Key saved — type a new key to replace it"
                    else "Paste your key here"
                )
            },
            visualTransformation = if (keyVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        imageVector = if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (keyVisible) "Hide key" else "Show key"
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("gemini_key_input")
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    viewModel.saveGeminiKey(keyInput.trim())
                    keyInput = ""
                },
                enabled = keyInput.isNotBlank(),
                modifier = Modifier.testTag("gemini_save_key")
            ) {
                Text("Save & test")
            }
            if (aiState.hasGeminiKey) {
                TextButton(onClick = { viewModel.testGeminiKey() }) { Text("Test key") }
                TextButton(onClick = { viewModel.clearGeminiKey() }) { Text("Remove key") }
            }
        }

        when (val test = aiState.keyTestState) {
            is KeyTestState.Idle -> Unit
            is KeyTestState.Testing -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = "Testing key…",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            is KeyTestState.Ok -> {
                Text(
                    text = "Key works — Gemini is ready.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is KeyTestState.Error -> {
                Text(
                    text = test.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (!aiState.keyStorageEncrypted) {
            Text(
                text = "Note: this device's secure keystore was unavailable, so the key is " +
                    "stored in app-private preferences instead of encrypted storage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Gemini model picker
        Text(
            text = "Gemini model for explanations",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 4.dp)
        )
        AiRadioRow(
            selected = currentModel == GeminiClient.DEFAULT_LLM_MODEL,
            title = "gemini-3.8-flash",
            subtitle = "Best quality on the free tier. Recommended.",
            onClick = { viewModel.setGeminiModel(GeminiClient.DEFAULT_LLM_MODEL) }
        )
        AiRadioRow(
            selected = currentModel == GeminiClient.LIGHT_LLM_MODEL,
            title = "gemini-3.5-flash-lite",
            subtitle = "Lighter and faster, lower quota use.",
            onClick = { viewModel.setGeminiModel(GeminiClient.LIGHT_LLM_MODEL) }
        )
        AiRadioRow(
            selected = isCustomModel,
            title = "Custom model name",
            subtitle = "Model names change fast — type any current Gemini model id.",
            onClick = {
                if (customModel.isNotBlank()) viewModel.setGeminiModel(customModel)
            }
        )
        if (isCustomModel || customModel.isNotBlank()) {
            OutlinedTextField(
                value = if (isCustomModel && customModel.isBlank()) currentModel else customModel,
                onValueChange = { customModel = it },
                label = { Text("Model name") },
                placeholder = { Text("e.g. gemini-3.8-flash") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = { viewModel.setGeminiModel(customModel) },
                enabled = customModel.isNotBlank() && customModel != currentModel
            ) {
                Text("Use this model")
            }
        }
        Text(
            text = "Embeddings always use gemini-embedding-001 (free tier, 768 dims).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GeminiStep(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = number,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}
