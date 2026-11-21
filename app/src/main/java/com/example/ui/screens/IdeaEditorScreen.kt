package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.BlockType
import com.example.data.local.IdeaBlockEntity
import com.example.data.local.IdeaNoteEntity
import com.example.data.local.IdeaUsageEntity
import com.example.domain.audio.PlaybackState
import com.example.domain.audio.RecordingState
import com.example.ui.components.AudioTileCard
import com.example.ui.components.LiveRecordingBar
import com.example.ui.components.UsageBadge
import com.example.ui.components.formatRelativeDate
import com.example.ui.viewmodel.LyricVaultViewModel
import java.util.Collections
import java.util.UUID

private data class EditableBlock(
    val uid: String = UUID.randomUUID().toString(),
    val blockType: String,
    val textContent: String = "",
    val audioFilePath: String? = null,
    val audioDurationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun IdeaEditorScreen(
    ideaId: Long?,
    startRecordingImmediately: Boolean,
    viewModel: LyricVaultViewModel,
    recordingState: RecordingState,
    playbackState: PlaybackState,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var currentId by remember { mutableLongStateOf(ideaId ?: 0L) }
    var createdAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var title by remember { mutableStateOf("") }
    var contextNote by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var usedCount by remember { mutableIntStateOf(0) }
    var usages by remember { mutableStateOf<List<IdeaUsageEntity>>(emptyList()) }
    val blocks = remember { mutableStateListOf<EditableBlock>() }
    var isLoaded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var permissionDeniedNotice by remember { mutableStateOf(false) }
    var autoRecordTriggered by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedNotice = false
            viewModel.startRecordingAudio()
        } else {
            permissionDeniedNotice = true
        }
    }

    fun requestOrStartRecording() {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            permissionDeniedNotice = false
            viewModel.startRecordingAudio()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(ideaId) {
        if (ideaId != null && ideaId > 0L) {
            val loaded = viewModel.loadIdeaOnce(ideaId)
            if (loaded != null) {
                currentId = loaded.idea.id
                createdAt = loaded.idea.createdAt
                title = loaded.idea.title
                contextNote = loaded.idea.contextNote
                tags = loaded.idea.tags
                usedCount = loaded.idea.usedCount
                usages = loaded.usages
                blocks.clear()
                loaded.orderedBlocks.forEach { b ->
                    blocks.add(
                        EditableBlock(
                            blockType = b.blockType,
                            textContent = b.textContent,
                            audioFilePath = b.audioFilePath,
                            audioDurationMs = b.audioDurationMs,
                            createdAt = b.createdAt
                        )
                    )
                }
            }
        }
        if (blocks.isEmpty()) {
            blocks.add(EditableBlock(blockType = BlockType.TEXT, textContent = ""))
        }
        isLoaded = true
    }

    LaunchedEffect(isLoaded, startRecordingImmediately) {
        if (isLoaded && startRecordingImmediately && !autoRecordTriggered) {
            autoRecordTriggered = true
            requestOrStartRecording()
        }
    }

    fun hasContentToSave(): Boolean {
        if (title.isNotBlank() || contextNote.isNotBlank() || tags.isNotBlank()) return true
        return blocks.any {
            (it.blockType == BlockType.TEXT && it.textContent.isNotBlank()) ||
                (it.blockType == BlockType.AUDIO && !it.audioFilePath.isNullOrBlank())
        }
    }

    fun persistAndExit() {
        // Dismiss the keyboard and drop focus first: otherwise the IME stays
        // open over the list screen after navigating back.
        keyboardController?.hide()
        focusManager.clearFocus()
        if (recordingState.isRecording) {
            val recorded = viewModel.stopRecordingAudio()
            if (recorded != null) {
                blocks.add(
                    EditableBlock(
                        blockType = BlockType.AUDIO,
                        textContent = "",
                        audioFilePath = recorded.filePath,
                        audioDurationMs = recorded.durationMs
                    )
                )
            }
        }
        viewModel.audioPlayer.stop()
        if (hasContentToSave()) {
            val entity = IdeaNoteEntity(
                id = currentId,
                title = title.trim(),
                contextNote = contextNote.trim(),
                tags = tags.trim(),
                usedCount = usedCount,
                createdAt = createdAt,
                updatedAt = System.currentTimeMillis()
            )
            val blockEntities = blocks
                .filter {
                    it.blockType == BlockType.AUDIO || it.textContent.isNotBlank() || blocks.size == 1
                }
                .mapIndexed { idx, b ->
                    IdeaBlockEntity(
                        ideaId = currentId,
                        orderIndex = idx,
                        blockType = b.blockType,
                        textContent = b.textContent,
                        audioFilePath = b.audioFilePath,
                        audioDurationMs = b.audioDurationMs,
                        createdAt = b.createdAt
                    )
                }
            viewModel.saveIdea(entity, blockEntities) {
                onNavigateBack()
            }
        } else {
            onNavigateBack()
        }
    }

    BackHandler {
        persistAndExit()
    }

    DisposableEffect(Unit) {
        onDispose {
            if (viewModel.recordingState.value.isRecording) {
                viewModel.cancelRecordingAudio()
            }
            viewModel.audioPlayer.stop()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentId == 0L) "New Idea" else "Edit Idea",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { persistAndExit() },
                        modifier = Modifier.testTag("idea_editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Save and go back"
                        )
                    }
                },
                actions = {
                    if (currentId > 0L) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.testTag("idea_editor_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete idea",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Button(
                        onClick = { persistAndExit() },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .height(40.dp)
                            .testTag("idea_editor_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // Free-flowing studio dock: add text block OR record inline audio melody tile right here.
            // NOTE: no imePadding() here on purpose. The manifest uses
            // adjustResize, so the window already shrinks above the keyboard;
            // adding imePadding counted the keyboard height twice and squeezed
            // the content to nothing while the keyboard animated in.
            // navigationBarsPadding is also unneeded: the outer Scaffold
            // already insets this screen for the navigation bar.
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimatedVisibility(visible = recordingState.isRecording) {
                        LiveRecordingBar(
                            recordingState = recordingState,
                            onStopAndSave = {
                                val recorded = viewModel.stopRecordingAudio()
                                if (recorded != null) {
                                    blocks.add(
                                        EditableBlock(
                                            blockType = BlockType.AUDIO,
                                            textContent = "",
                                            audioFilePath = recorded.filePath,
                                            audioDurationMs = recorded.durationMs
                                        )
                                    )
                                    // Immediately add a fresh text block underneath for two-and-fro writing
                                    blocks.add(EditableBlock(blockType = BlockType.TEXT, textContent = ""))
                                }
                            },
                            onCancel = { viewModel.cancelRecordingAudio() }
                        )
                    }

                    if (!recordingState.isRecording) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    blocks.add(EditableBlock(blockType = BlockType.TEXT, textContent = ""))
                                },
                                shape = RoundedCornerShape(14.dp),
                                // Tight horizontal padding: the default 24dp
                                // plus icon + label wrapped the text to two
                                // lines, and the fixed 48dp height clipped it.
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("add_text_block_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TextFields,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "+ Text Block",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = { requestOrStartRecording() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("record_audio_tile_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Record Melody",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (permissionDeniedNotice || recordingState.errorMessage != null) {
                    item(key = "mic_notice") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = recordingState.errorMessage
                                    ?: "Microphone permission is required to record melody tiles. Tap Record Melody again to allow.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            )
                        }
                    }
                }

                // Header Metadata Card (Title, "Why this piqued my interest", Tags, Usage)
                item(key = "idea_metadata") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UsageBadge(usedCount = usedCount)
                                if (usedCount > 0 && currentId > 0L) {
                                    TextButton(
                                        onClick = {
                                            usedCount = 0
                                            usages = emptyList()
                                            viewModel.resetIdeaUsage(currentId)
                                        },
                                        modifier = Modifier.testTag("reset_idea_usage_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.RestartAlt,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Mark Unused")
                                    }
                                }
                            }

                            BasicTextField(
                                value = title,
                                onValueChange = { title = it },
                                textStyle = MaterialTheme.typography.headlineSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("idea_title_input"),
                                decorationBox = { inner ->
                                    if (title.isBlank()) {
                                        Text(
                                            text = "Hook word, phrase, or concept title...",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    inner()
                                }
                            )

                            OutlinedTextField(
                                value = contextNote,
                                onValueChange = { contextNote = it },
                                // Short label on purpose: the old one wrapped
                                // to two lines and overlapped the outline.
                                label = { Text("Why it piqued my interest", maxLines = 1) },
                                placeholder = {
                                    Text("e.g. Good for a late-night moody verse or psychological bridge...")
                                },
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("idea_context_input")
                            )

                            OutlinedTextField(
                                value = tags,
                                onValueChange = { tags = it },
                                // Short label on purpose: the old one wrapped
                                // to two lines and overlapped the outline.
                                label = { Text("Mood / concept tags", maxLines = 1) },
                                placeholder = { Text("metro, solitude, ego, hook") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("idea_tags_input")
                            )

                            if (usages.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "USED IN SONGS",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        usages.forEach { usage ->
                                            Text(
                                                text = "• ${usage.songTitleSnapshot} (${formatRelativeDate(usage.usedAt)})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "blocks_section_label") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FREE-FLOWING LYRICS & MELODIES",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${blocks.size} block${if (blocks.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Interleaved Text & Audio Blocks
                itemsIndexed(
                    items = blocks,
                    key = { _, item -> item.uid }
                ) { index, block ->
                    if (block.blockType == BlockType.TEXT) {
                        Surface(
                            // animateItem is a LazyItemScope member, not a
                            // top-level function: no import for it.
                            modifier = Modifier.animateItem(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "LYRIC / THOUGHT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (index > 0) {
                                            IconButton(
                                                onClick = { Collections.swap(blocks, index, index - 1) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.ArrowUpward,
                                                    contentDescription = "Move text block up",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        if (index < blocks.lastIndex) {
                                            IconButton(
                                                onClick = { Collections.swap(blocks, index, index + 1) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.ArrowDownward,
                                                    contentDescription = "Move text block down",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        if (blocks.size > 1) {
                                            IconButton(
                                                onClick = { blocks.removeAt(index) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.DeleteOutline,
                                                    contentDescription = "Remove text block",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                BasicTextField(
                                    value = block.textContent,
                                    onValueChange = { updated ->
                                        blocks[index] = block.copy(textContent = updated)
                                    },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .testTag("idea_text_block_$index"),
                                    decorationBox = { inner ->
                                        if (block.textContent.isBlank()) {
                                            Text(
                                                text = "Write a single word, lyric fragment, or concept... then tap Record Melody below to interleave audio.",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                            )
                                        }
                                        inner()
                                    }
                                )
                            }
                        }
                    } else {
                        AudioTileCard(
                            modifier = Modifier.animateItem(),
                            audioPath = block.audioFilePath,
                            durationMs = block.audioDurationMs,
                            caption = block.textContent,
                            playbackState = playbackState,
                            onTogglePlay = { path -> viewModel.audioPlayer.togglePlay(path) },
                            onSeek = { path, frac -> viewModel.audioPlayer.seekTo(path, frac) },
                            editable = true,
                            onCaptionChange = { updated ->
                                blocks[index] = block.copy(textContent = updated)
                            },
                            onMoveUp = if (index > 0) {
                                { Collections.swap(blocks, index, index - 1) }
                            } else null,
                            onMoveDown = if (index < blocks.lastIndex) {
                                { Collections.swap(blocks, index, index + 1) }
                            } else null,
                            onDelete = {
                                val removed = blocks.removeAt(index)
                                viewModel.deleteAudioFile(removed.audioFilePath)
                                if (blocks.isEmpty()) {
                                    blocks.add(EditableBlock(blockType = BlockType.TEXT, textContent = ""))
                                }
                            }
                        )
                    }
                }

                item(key = "editor_bottom_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this idea?") },
            text = { Text("This will permanently remove the note and any melody recordings inside it from your phone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteIdea(currentId) {
                            onNavigateBack()
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
