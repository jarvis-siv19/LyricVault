package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.BlockType
import com.example.data.local.IdeaNoteWithDetails
import com.example.data.local.SongEntity
import com.example.domain.audio.PlaybackState
import com.example.domain.matching.MatchMode
import com.example.domain.matching.SplitMatchResult
import com.example.domain.matching.VaultMatchItem
import com.example.domain.matching.VaultMatchResult
import com.example.ui.components.AudioTileCard
import com.example.ui.components.MatchScoreBadge
import com.example.ui.components.UsageBadge
import com.example.ui.viewmodel.ExplanationState
import com.example.ui.viewmodel.LyricVaultViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongwritingScreen(
    songId: Long?,
    allIdeas: List<IdeaNoteWithDetails>,
    playbackState: PlaybackState,
    viewModel: LyricVaultViewModel,
    onOpenIdea: (Long) -> Unit,
    onOpenAiSettings: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var currentSongId by remember { mutableLongStateOf(songId ?: 0L) }
    var createdAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var songTitle by remember { mutableStateOf("") }
    var vibeDescription by remember { mutableStateOf("") }
    var lyricsField by remember { mutableStateOf(TextFieldValue("")) }
    // Per-song matching instruction: overrides the global one from AI
    // settings for this song; blank = use the global instruction.
    var songInstruction by remember { mutableStateOf("") }

    // 0 = Write Lyrics, 1 = Vault Suggestions (used on compact phone screens)
    var activeCompactTab by remember { mutableIntStateOf(0) }
    var matchMode by remember { mutableStateOf(MatchMode.WHOLE_SONG_VIBE) }
    var specificLineQuery by remember { mutableStateOf("") }
    var matchResult by remember { mutableStateOf(SplitMatchResult()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLinePickerDialog by remember { mutableStateOf(false) }
    var isLoaded by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(songId) {
        if (songId != null && songId > 0L) {
            val loaded = viewModel.loadSongOnce(songId)
            if (loaded != null) {
                currentSongId = loaded.id
                createdAt = loaded.createdAt
                songTitle = loaded.title
                vibeDescription = loaded.vibeDescription
                lyricsField = TextFieldValue(loaded.lyrics)
                songInstruction = loaded.customInstruction ?: ""
            }
        }
        isLoaded = true
    }

    // Extract currently highlighted text inside the lyrics editor (if any)
    val highlightedText = remember(lyricsField) {
        val sel = lyricsField.selection
        if (!sel.collapsed && sel.min >= 0 && sel.max <= lyricsField.text.length) {
            lyricsField.text.substring(sel.min, sel.max).trim()
        } else {
            ""
        }
    }

    // Extract current line under the cursor as a convenient one-tap target
    val cursorLine = remember(lyricsField) {
        val text = lyricsField.text
        if (text.isBlank()) return@remember ""
        val cursor = lyricsField.selection.start.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
        text.substring(lineStart, lineEnd).trim()
    }

    // Live offline grounded matching whenever vibe, lyrics, line query, or vault changes
    // The effective query is shared with "Why this fits" explanations, so it
    // lives in one place.
    fun effectiveQuery(): String = when (matchMode) {
        MatchMode.WHOLE_SONG_VIBE -> {
            buildString {
                if (vibeDescription.isNotBlank()) {
                    appendLine(vibeDescription)
                    appendLine(vibeDescription) // weight the song's stated vibe heavily
                }
                if (songTitle.isNotBlank()) appendLine(songTitle)
                if (lyricsField.text.isNotBlank()) {
                    append(lyricsField.text.takeLast(600))
                }
            }.trim()
        }
        MatchMode.SPECIFIC_LINE -> specificLineQuery.trim()
    }

    val aiUiState by viewModel.aiUiState.collectAsState()
    val explanations by viewModel.explanations.collectAsState()
    val llmConfigured = aiUiState.enabled && aiUiState.llmId != com.example.ai.AiSettings.LLM_NONE

    LaunchedEffect(
        vibeDescription, lyricsField.text, specificLineQuery, matchMode, allIdeas,
        songInstruction, aiUiState.customInstruction
    ) {
        delay(180L)
        matchResult = viewModel.computeMatches(
            ideas = allIdeas,
            query = effectiveQuery(),
            mode = matchMode,
            songInstruction = songInstruction
        )
    }

    // The instruction actually in effect for this song (per-song wins,
    // else the global one). Shown in the suggestions pane.
    val activeInstruction = remember(songInstruction, aiUiState.customInstruction) {
        com.example.ai.AiMatchingService.resolveInstruction(
            songInstruction,
            aiUiState.customInstruction
        )
    }

    fun persistSong(then: (Long) -> Unit = {}) {
        val hasAny = songTitle.isNotBlank() || vibeDescription.isNotBlank() ||
            lyricsField.text.isNotBlank() || songInstruction.isNotBlank()
        if (!hasAny && currentSongId == 0L) {
            then(0L)
            return
        }
        val entity = SongEntity(
            id = currentSongId,
            title = songTitle.trim(),
            vibeDescription = vibeDescription.trim(),
            lyrics = lyricsField.text,
            customInstruction = songInstruction.trim().ifBlank { null },
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
        viewModel.saveSong(entity) { savedId ->
            currentSongId = savedId
            then(savedId)
        }
    }

    fun markIdeaUsedHere(ideaId: Long, alsoInsertText: String? = null) {
        if (!alsoInsertText.isNullOrBlank()) {
            val currentText = lyricsField.text
            val separator = when {
                currentText.isBlank() -> ""
                currentText.endsWith("\n") -> "\n"
                else -> "\n\n"
            }
            val updated = currentText + separator + alsoInsertText.trim()
            lyricsField = TextFieldValue(
                text = updated,
                selection = TextRange(updated.length)
            )
        }
        persistSong { savedSongId ->
            viewModel.markIdeaUsed(
                ideaId = ideaId,
                songId = savedSongId,
                songTitle = songTitle.ifBlank { "Untitled Song" }
            )
        }
    }

    fun exitToPrevious() {
        // Dismiss the keyboard and drop focus first: otherwise the IME stays
        // open over the list screen after navigating back.
        keyboardController?.hide()
        focusManager.clearFocus()
        viewModel.audioPlayer.stop()
        persistSong { onNavigateBack() }
    }

    BackHandler {
        exitToPrevious()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.audioPlayer.stop()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SONGWRITING MODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = songTitle.ifBlank { "Untitled Song" },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { exitToPrevious() },
                        modifier = Modifier.testTag("songwriting_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Save song and go back"
                        )
                    }
                },
                actions = {
                    if (currentSongId > 0L) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.testTag("songwriting_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete song",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Button(
                        onClick = {
                            persistSong {
                                viewModel.showMessage("Song saved locally.")
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .height(40.dp)
                            .testTag("songwriting_save_button")
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
        }
    ) { innerPadding ->
        // NOTE: no imePadding() here on purpose. The manifest uses
        // adjustResize, so the window already shrinks above the keyboard;
        // adding imePadding counted the keyboard height twice and squeezed
        // the whole editor to a black screen while the keyboard animated in.
        // navigationBarsPadding is also unneeded: the outer Scaffold
        // already insets this screen for the navigation bar.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isSplitLayout = maxWidth >= 760.dp

            if (isSplitLayout) {
                // Tablet / Foldable Side-by-Side Split Studio
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LyricEditorPane(
                        songTitle = songTitle,
                        onTitleChange = { songTitle = it },
                        vibeDescription = vibeDescription,
                        onVibeChange = { vibeDescription = it },
                        customInstruction = songInstruction,
                        onCustomInstructionChange = {
                            songInstruction = it.take(com.example.ai.AiSettings.MAX_INSTRUCTION_CHARS)
                        },
                        globalInstruction = aiUiState.customInstruction,
                        lyricsField = lyricsField,
                        onLyricsChange = { lyricsField = it },
                        highlightedText = highlightedText,
                        cursorLine = cursorLine,
                        onMatchSpecificLine = { line ->
                            specificLineQuery = line
                            matchMode = MatchMode.SPECIFIC_LINE
                        },
                        onOpenLinePicker = { showLinePickerDialog = true },
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                    )

                    SuggestionsPane(
                        matchMode = matchMode,
                        onMatchModeChange = { matchMode = it },
                        specificLineQuery = specificLineQuery,
                        onSpecificLineQueryChange = { specificLineQuery = it },
                        vibeDescription = vibeDescription,
                        matchResult = matchResult,
                        totalVaultIdeas = allIdeas.size,
                        playbackState = playbackState,
                        onTogglePlay = { viewModel.audioPlayer.togglePlay(it) },
                        onSeek = { p, f -> viewModel.audioPlayer.seekTo(p, f) },
                        onMarkUsed = { id -> markIdeaUsedHere(id, alsoInsertText = null) },
                        onInsertAndMarkUsed = { id, snippet -> markIdeaUsedHere(id, alsoInsertText = snippet) },
                        onOpenIdea = onOpenIdea,
                        onOpenLinePicker = { showLinePickerDialog = true },
                        aiEnabled = aiUiState.enabled,
                        llmConfigured = llmConfigured,
                        activeInstruction = activeInstruction,
                        explanations = explanations,
                        onExplain = { id ->
                            viewModel.explainMatch(
                                id,
                                effectiveQuery(),
                                songInstruction = songInstruction
                            )
                        },
                        onOpenAiSettings = onOpenAiSettings,
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                    )
                }
            } else {
                // Compact Mobile Layout: Seamless Toggle between Lyrics & Vault Suggestions
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 10.dp)
                    ) {
                        SegmentedButton(
                            selected = activeCompactTab == 0,
                            onClick = { activeCompactTab = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.EditNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("songwriting_tab_write")
                        ) {
                            Text("Write Lyrics")
                        }
                        SegmentedButton(
                            selected = activeCompactTab == 1,
                            onClick = { activeCompactTab = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("songwriting_tab_suggestions")
                        ) {
                            val badge = if (matchResult.totalCount > 0) " (${matchResult.totalCount})" else ""
                            Text("Vault Matches$badge")
                        }
                    }

                    if (activeCompactTab == 0) {
                        LyricEditorPane(
                            songTitle = songTitle,
                            onTitleChange = { songTitle = it },
                            vibeDescription = vibeDescription,
                            onVibeChange = { vibeDescription = it },
                            customInstruction = songInstruction,
                            onCustomInstructionChange = {
                                songInstruction = it.take(com.example.ai.AiSettings.MAX_INSTRUCTION_CHARS)
                            },
                            globalInstruction = aiUiState.customInstruction,
                            lyricsField = lyricsField,
                            onLyricsChange = { lyricsField = it },
                            highlightedText = highlightedText,
                            cursorLine = cursorLine,
                            onMatchSpecificLine = { line ->
                                specificLineQuery = line
                                matchMode = MatchMode.SPECIFIC_LINE
                                activeCompactTab = 1
                            },
                            onOpenLinePicker = { showLinePickerDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    } else {
                        SuggestionsPane(
                            matchMode = matchMode,
                            onMatchModeChange = { matchMode = it },
                            specificLineQuery = specificLineQuery,
                            onSpecificLineQueryChange = { specificLineQuery = it },
                            vibeDescription = vibeDescription,
                            matchResult = matchResult,
                            totalVaultIdeas = allIdeas.size,
                            playbackState = playbackState,
                            onTogglePlay = { viewModel.audioPlayer.togglePlay(it) },
                            onSeek = { p, f -> viewModel.audioPlayer.seekTo(p, f) },
                            onMarkUsed = { id -> markIdeaUsedHere(id, alsoInsertText = null) },
                            onInsertAndMarkUsed = { id, snippet ->
                                markIdeaUsedHere(id, alsoInsertText = snippet)
                                activeCompactTab = 0
                            },
                            onOpenIdea = onOpenIdea,
                            onOpenLinePicker = { showLinePickerDialog = true },
                            aiEnabled = aiUiState.enabled,
                            llmConfigured = llmConfigured,
                            activeInstruction = activeInstruction,
                            explanations = explanations,
                            onExplain = { id ->
                                viewModel.explainMatch(
                                    id,
                                    effectiveQuery(),
                                    songInstruction = songInstruction
                                )
                            },
                            onOpenAiSettings = onOpenAiSettings,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showLinePickerDialog) {
        val nonBlankLines = remember(lyricsField.text) {
            lyricsField.text.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct()
        }
        AlertDialog(
            onDismissRequest = { showLinePickerDialog = false },
            title = { Text("Pick a line to match in your Vault") },
            text = {
                if (nonBlankLines.isEmpty()) {
                    Text("Write at least one lyric line first, or type any word/phrase directly in the Line Match tab.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(280.dp)
                    ) {
                        items(nonBlankLines) { line ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        specificLineQuery = line
                                        matchMode = MatchMode.SPECIFIC_LINE
                                        activeCompactTab = 1
                                        showLinePickerDialog = false
                                    }
                            ) {
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLinePickerDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this song?") },
            text = { Text("This removes the song draft from your phone. Your original vault ideas stay safe.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteSong(currentSongId) {
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

@Composable
private fun LyricEditorPane(
    songTitle: String,
    onTitleChange: (String) -> Unit,
    vibeDescription: String,
    onVibeChange: (String) -> Unit,
    customInstruction: String,
    onCustomInstructionChange: (String) -> Unit,
    globalInstruction: String,
    lyricsField: TextFieldValue,
    onLyricsChange: (TextFieldValue) -> Unit,
    highlightedText: String,
    cursorLine: String,
    onMatchSpecificLine: (String) -> Unit,
    onOpenLinePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Vibe & Premise Card (directly from Mr. Music's workflow!)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BasicTextField(
                    value = songTitle,
                    onValueChange = onTitleChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("song_title_input"),
                    decorationBox = { inner ->
                        if (songTitle.isBlank()) {
                            Text(
                                text = "Song title...",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        }
                        inner()
                    }
                )

                OutlinedTextField(
                    value = vibeDescription,
                    onValueChange = onVibeChange,
                    // Short label on purpose: the old one wrapped to two
                    // lines and overlapped the outline.
                    label = { Text("Song Vibe & Premise", maxLines = 1) },
                    placeholder = {
                        Text("e.g. Late-night metro ride overthinking the masks people wear...")
                    },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("song_vibe_input")
                )

                OutlinedTextField(
                    value = customInstruction,
                    onValueChange = onCustomInstructionChange,
                    // Short label on purpose (see the vibe field above):
                    // the full wording lives in the supporting text.
                    label = { Text("Matching instruction", maxLines = 1) },
                    placeholder = {
                        Text(
                            if (globalInstruction.isNotBlank()) {
                                "Overrides your global instruction for this song…"
                            } else {
                                "e.g. Match by mood and imagery, not rhyme…"
                            }
                        )
                    },
                    supportingText = {
                        Text(
                            if (customInstruction.isBlank() && globalInstruction.isNotBlank()) {
                                "Blank — using your global instruction from AI settings."
                            } else {
                                "For this song only. Steers AI matching + “Why this fits”."
                            }
                        )
                    },
                    minLines = 1,
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("song_instruction_input")
                )
            }
        }

        // Line / Highlight Quick Match Action Bar
        val lineTarget = highlightedText.ifBlank { cursorLine }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.FindInPage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (highlightedText.isNotBlank()) {
                            "Highlighted: \"$highlightedText\""
                        } else if (cursorLine.isNotBlank()) {
                            "Active line: \"$cursorLine\""
                        } else {
                            "Highlight any line or word to check similar notes"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (lineTarget.isNotBlank()) {
                    Button(
                        onClick = { onMatchSpecificLine(lineTarget) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("match_selected_line_button")
                    ) {
                        Text(
                            text = if (highlightedText.isNotBlank()) "Match Selection" else "Match Line",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else if (lyricsField.text.isNotBlank()) {
                    TextButton(onClick = onOpenLinePicker) {
                        Text("Pick Line")
                    }
                }
            }
        }

        // Distraction-Free Lyrics Sheet
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LYRICS CANVAS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (lyricsField.text.isNotBlank()) {
                        TextButton(
                            onClick = onOpenLinePicker,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.testTag("pick_line_dialog_button")
                        ) {
                            Text("Pick Line to Match", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                BasicTextField(
                    value = lyricsField,
                    onValueChange = onLyricsChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .testTag("song_lyrics_input"),
                    decorationBox = { inner ->
                        if (lyricsField.text.isBlank()) {
                            Text(
                                text = "Start writing your verses, pre-chorus, or hook...\n\nTip: Highlight any word or line to instantly ask your Vault: \"Have I saved a similar line or idea I can use here?\"",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        inner()
                    }
                )
            }
        }
    }
}

@Composable
private fun SuggestionsPane(
    matchMode: MatchMode,
    onMatchModeChange: (MatchMode) -> Unit,
    specificLineQuery: String,
    onSpecificLineQueryChange: (String) -> Unit,
    vibeDescription: String,
    matchResult: SplitMatchResult,
    totalVaultIdeas: Int,
    playbackState: PlaybackState,
    onTogglePlay: (String) -> Unit,
    onSeek: (String, Float) -> Unit,
    onMarkUsed: (Long) -> Unit,
    onInsertAndMarkUsed: (Long, String) -> Unit,
    onOpenIdea: (Long) -> Unit,
    onOpenLinePicker: () -> Unit,
    aiEnabled: Boolean,
    llmConfigured: Boolean,
    activeInstruction: String,
    explanations: Map<Long, ExplanationState>,
    onExplain: (Long) -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!aiEnabled) {
            item(key = "ai_enable_banner") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Smarter matching is available",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "AI matching finds ideas by meaning, not just shared words — on your phone, offline.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        TextButton(onClick = onOpenAiSettings) {
                            Text("Enable")
                        }
                    }
                }
            }
        }
        item(key = "suggestions_controls") {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "YOUR OWN NOTES ONLY • OFFLINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "$totalVaultIdeas notes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Mode Switcher: Whole Song Vibe vs Specific Line.
                    // A plain Row with weighted chips: the old LazyRow let
                    // the second chip run past the card edge, half cut off.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModeChip(
                            selected = matchMode == MatchMode.WHOLE_SONG_VIBE,
                            label = "Whole Song Vibe",
                            onClick = { onMatchModeChange(MatchMode.WHOLE_SONG_VIBE) },
                            tag = "match_mode_vibe",
                            modifier = Modifier.weight(1f)
                        )
                        ModeChip(
                            selected = matchMode == MatchMode.SPECIFIC_LINE,
                            label = "Specific Line",
                            onClick = { onMatchModeChange(MatchMode.SPECIFIC_LINE) },
                            tag = "match_mode_line",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // The instruction actually steering these matches
                    // (per-song, else the global one from AI settings).
                    if (aiEnabled && activeInstruction.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Matching with instruction: “" +
                                    activeInstruction.take(80) +
                                    (if (activeInstruction.length > 80) "…" else "") + "”",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (matchMode == MatchMode.SPECIFIC_LINE) {
                        OutlinedTextField(
                            value = specificLineQuery,
                            onValueChange = onSpecificLineQueryChange,
                            label = { Text("Line, phrase, or word to match", maxLines = 1) },
                            placeholder = { Text("Paste or type a line to check your Vault...") },
                            trailingIcon = {
                                if (specificLineQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSpecificLineQueryChange("") }) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Clear line query"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("specific_line_query_input")
                        )
                        TextButton(
                            onClick = onOpenLinePicker,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("Or pick a line from your current lyrics")
                        }
                    } else {
                        Text(
                            text = if (vibeDescription.isBlank()) {
                                "Write a short vibe sentence at the top of your song to scan your Vault automatically."
                            } else {
                                "Matching against: \"$vibeDescription\""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (totalVaultIdeas == 0) {
            item(key = "no_vault_ideas") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "Your Idea Vault is empty. Capture a few lyrics, words, or melodies in the Vault tab and they will surface here automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else if (matchResult.totalCount == 0) {
            item(key = "no_matches_yet") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (matchMode == MatchMode.SPECIFIC_LINE && specificLineQuery.isBlank()) {
                                "Select or enter a line to search your Vault"
                            } else {
                                "No matching saved ideas yet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "LyricVault never invents fake AI lines — it only surfaces ideas you actually wrote. Try expanding your song vibe, adding mood words, or picking a specific line.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Two sub-sections by matching type (issue #8): AI matches
            // first, then keyword matches. The same idea can appear in
            // both — each section shows what its own engine found, with
            // its own score and reason. With AI off there is no AI
            // section at all (the banner above already explains why).
            if (aiEnabled) {
                matchTypeSection(
                    prefix = "ai",
                    title = "AI MATCHES",
                    icon = Icons.Filled.AutoAwesome,
                    result = matchResult.aiMatches,
                    emptyText = "No ideas matched by meaning for this query.",
                    playbackState = playbackState,
                    onTogglePlay = onTogglePlay,
                    onSeek = onSeek,
                    onMarkUsed = onMarkUsed,
                    onInsertAndMarkUsed = onInsertAndMarkUsed,
                    onOpenIdea = onOpenIdea,
                    llmConfigured = llmConfigured,
                    explanations = explanations,
                    onExplain = onExplain
                )
            }
            matchTypeSection(
                prefix = "keyword",
                title = "KEYWORD MATCHES",
                icon = Icons.Filled.Search,
                result = matchResult.keywordMatches,
                emptyText = "No ideas matched by shared words for this query.",
                playbackState = playbackState,
                onTogglePlay = onTogglePlay,
                onSeek = onSeek,
                onMarkUsed = onMarkUsed,
                onInsertAndMarkUsed = onInsertAndMarkUsed,
                onOpenIdea = onOpenIdea,
                llmConfigured = llmConfigured,
                explanations = explanations,
                onExplain = onExplain
            )
        }
    }
}

/**
 * One matching-type section inside the suggestions list (issue #8): a
 * header naming the engine ("AI MATCHES" / "KEYWORD MATCHES"), then fresh
 * ideas, then already-used ideas. All item keys carry the section prefix
 * because the same idea can appear in both sections.
 */
private fun LazyListScope.matchTypeSection(
    prefix: String,
    title: String,
    icon: ImageVector,
    result: VaultMatchResult,
    emptyText: String,
    playbackState: PlaybackState,
    onTogglePlay: (String) -> Unit,
    onSeek: (String, Float) -> Unit,
    onMarkUsed: (Long) -> Unit,
    onInsertAndMarkUsed: (Long, String) -> Unit,
    onOpenIdea: (Long) -> Unit,
    llmConfigured: Boolean,
    explanations: Map<Long, ExplanationState>,
    onExplain: (Long) -> Unit
) {
    item(key = "${prefix}_header") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "$title (${result.totalCount})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    if (result.totalCount == 0) {
        item(key = "${prefix}_empty") {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        return
    }
    if (result.freshUnusedMatches.isNotEmpty()) {
        item(key = "${prefix}_fresh_header") {
            Text(
                text = "FRESH UNUSED (${result.freshUnusedMatches.size})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        items(
            items = result.freshUnusedMatches,
            key = { "${prefix}_fresh_${it.ideaDetail.idea.id}" }
        ) { item ->
            VaultSuggestionCard(
                match = item,
                isAlreadyUsedSection = false,
                playbackState = playbackState,
                onTogglePlay = onTogglePlay,
                onSeek = onSeek,
                onMarkUsed = { onMarkUsed(item.ideaDetail.idea.id) },
                onInsertAndMarkUsed = {
                    onInsertAndMarkUsed(item.ideaDetail.idea.id, item.ideaDetail.combinedLyricText.ifBlank { item.bestSnippet })
                },
                onOpenIdea = { onOpenIdea(item.ideaDetail.idea.id) },
                llmConfigured = llmConfigured,
                explanation = explanations[item.ideaDetail.idea.id] ?: ExplanationState.Idle,
                onExplain = { onExplain(item.ideaDetail.idea.id) }
            )
        }
    }
    if (result.alreadyUsedMatches.isNotEmpty()) {
        item(key = "${prefix}_used_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "ALREADY USED — REUSE IF YOU WANT (${result.alreadyUsedMatches.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
        items(
            items = result.alreadyUsedMatches,
            key = { "${prefix}_used_${it.ideaDetail.idea.id}" }
        ) { item ->
            VaultSuggestionCard(
                match = item,
                isAlreadyUsedSection = true,
                playbackState = playbackState,
                onTogglePlay = onTogglePlay,
                onSeek = onSeek,
                onMarkUsed = { onMarkUsed(item.ideaDetail.idea.id) },
                onInsertAndMarkUsed = {
                    onInsertAndMarkUsed(item.ideaDetail.idea.id, item.ideaDetail.combinedLyricText.ifBlank { item.bestSnippet })
                },
                onOpenIdea = { onOpenIdea(item.ideaDetail.idea.id) },
                llmConfigured = llmConfigured,
                explanation = explanations[item.ideaDetail.idea.id] ?: ExplanationState.Idle,
                onExplain = { onExplain(item.ideaDetail.idea.id) }
            )
        }
    }
}

@Composable
private fun ModeChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .height(40.dp)
            .testTag(tag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VaultSuggestionCard(
    match: VaultMatchItem,
    isAlreadyUsedSection: Boolean,
    playbackState: PlaybackState,
    onTogglePlay: (String) -> Unit,
    onSeek: (String, Float) -> Unit,
    onMarkUsed: () -> Unit,
    onInsertAndMarkUsed: () -> Unit,
    onOpenIdea: () -> Unit,
    llmConfigured: Boolean,
    explanation: ExplanationState,
    onExplain: () -> Unit
) {
    val detail = match.ideaDetail
    val audioBlocks = remember(detail) {
        detail.orderedBlocks.filter { it.blockType == BlockType.AUDIO && !it.audioFilePath.isNullOrBlank() }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isAlreadyUsedSection) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isAlreadyUsedSection) {
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("suggestion_card_${detail.idea.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MatchScoreBadge(scorePercent = match.scorePercent)
                    UsageBadge(usedCount = detail.idea.usedCount)
                }
                TextButton(
                    onClick = onOpenIdea,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Open Note", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (isAlreadyUsedSection) {
                val songNames = detail.usages.map { it.songTitleSnapshot }.distinct().take(2)
                val usedInfo = if (songNames.isNotEmpty()) {
                    "Already used ${detail.idea.usedCount}x (in ${songNames.joinToString(", ")}) — matches too, reuse if you want."
                } else {
                    "Already used ${detail.idea.usedCount}x — matches too, reuse if you want."
                }
                Text(
                    text = usedInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Text(
                text = match.matchReason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )

            Text(
                text = detail.displayHeadline,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (detail.combinedLyricText.isNotBlank() && detail.combinedLyricText != detail.displayHeadline) {
                Text(
                    text = detail.combinedLyricText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (detail.idea.contextNote.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = "Your note: ${detail.idea.contextNote}",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Inline playable melody tile directly inside suggestions!
            audioBlocks.take(1).forEach { block ->
                AudioTileCard(
                    audioPath = block.audioFilePath,
                    durationMs = block.audioDurationMs,
                    caption = block.textContent,
                    playbackState = playbackState,
                    onTogglePlay = onTogglePlay,
                    onSeek = onSeek,
                    editable = false
                )
            }

            // "Why this fits" — one on-device (or Gemini) sentence, on demand.
            if (llmConfigured) {
                when (explanation) {
                    is ExplanationState.Idle -> {
                        TextButton(
                            onClick = onExplain,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Why this fits")
                        }
                    }
                    is ExplanationState.Loading -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Text(
                                text = "Thinking…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is ExplanationState.Done -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        ) {
                            Text(
                                text = explanation.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                    is ExplanationState.Error -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = explanation.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onExplain) { Text("Retry") }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onInsertAndMarkUsed,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("insert_and_mark_used_${detail.idea.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isAlreadyUsedSection) "Insert & Reuse" else "Insert & Use",
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onMarkUsed,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("mark_used_only_${detail.idea.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isAlreadyUsedSection) "Reuse (+1)" else "Mark Used",
                        maxLines = 1
                    )
                }
            }
        }
    }
}
