package com.example.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.BackupSettingsScreen
import com.example.ui.screens.IdeaEditorScreen
import com.example.ui.screens.SongsListScreen
import com.example.ui.screens.SongwritingScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.viewmodel.LyricVaultViewModel

object Routes {
    const val VAULT = "vault"
    const val STUDIO = "studio"
    const val BACKUP = "backup"
    const val IDEA_EDITOR = "idea_editor/{ideaId}/{autoRecord}"
    const val SONGWRITING = "songwriting/{songId}"

    fun ideaEditor(ideaId: Long = 0L, autoRecord: Boolean = false) = "idea_editor/$ideaId/$autoRecord"
    fun songwriting(songId: Long = 0L) = "songwriting/$songId"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val topLevelDestinations = listOf(
    TopLevelDestination(
        route = Routes.VAULT,
        label = "Vault",
        selectedIcon = Icons.Filled.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic
    ),
    TopLevelDestination(
        route = Routes.STUDIO,
        label = "Studio",
        selectedIcon = Icons.Filled.EditNote,
        unselectedIcon = Icons.Outlined.EditNote
    ),
    TopLevelDestination(
        route = Routes.BACKUP,
        label = "Backup",
        selectedIcon = Icons.Filled.CloudDone,
        unselectedIcon = Icons.Outlined.CloudDone
    )
)

// Navigation transitions. The NavHost default is a 700ms crossfade, which
// left both screens semi-transparent at once (the double-exposed look in
// recordings). Editor screens slide in/out opaque instead; top-level tabs
// keep a short fade, which is the standard tab motion.
private val tabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(animationSpec = tween(200))
}
private val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(animationSpec = tween(200))
}
private val editorEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300))
}
private val editorExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(300)) +
        fadeOut(animationSpec = tween(300))
}
private val editorPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(300))
}
private val editorPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
}

@Composable
fun LyricVaultApp(viewModel: LyricVaultViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val allIdeas by viewModel.allIdeas.collectAsStateWithLifecycle()
    val filteredIdeas by viewModel.filteredIdeas.collectAsStateWithLifecycle()
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.vaultSearchQuery.collectAsStateWithLifecycle()
    val vaultFilter by viewModel.vaultFilter.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    LaunchedEffect(playbackState.errorMessage) {
        val err = playbackState.errorMessage
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
            viewModel.audioPlayer.clearError()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showTopLevelChrome = currentRoute in setOf(Routes.VAULT, Routes.STUDIO, Routes.BACKUP)

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Never carry an open keyboard across a navigation: without this the IME
    // stays up over the next screen (e.g. over the Vault list after backing
    // out of the editor) until the user dismisses it by hand.
    LaunchedEffect(currentRoute) {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    val unusedCount = remember(allIdeas) { allIdeas.count { it.idea.usedCount == 0 } }
    val audioClipsCount = remember(allIdeas) { allIdeas.sumOf { it.audioBlockCount } }

    fun navigateTopLevel(route: String) {
        if (currentRoute == route) return
        navController.navigate(route) {
            popUpTo(Routes.VAULT) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (showTopLevelChrome && !useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelChrome && useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.VAULT,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(
                        route = Routes.VAULT,
                        enterTransition = tabEnter,
                        exitTransition = tabExit,
                        popEnterTransition = tabEnter,
                        popExitTransition = tabExit
                    ) {
                        VaultScreen(
                            allIdeasCount = allIdeas.size,
                            unusedIdeasCount = unusedCount,
                            audioClipsCount = audioClipsCount,
                            filteredIdeas = filteredIdeas,
                            searchQuery = searchQuery,
                            activeFilter = vaultFilter,
                            onSearchChange = viewModel::setVaultSearchQuery,
                            onFilterChange = viewModel::setVaultFilter,
                            onOpenIdea = { id ->
                                navController.navigate(Routes.ideaEditor(ideaId = id, autoRecord = false))
                            },
                            onCreateNewIdea = {
                                navController.navigate(Routes.ideaEditor(ideaId = 0L, autoRecord = false))
                            },
                            onCreateVoiceIdea = {
                                navController.navigate(Routes.ideaEditor(ideaId = 0L, autoRecord = true))
                            },
                            onGoToBackupImport = { navigateTopLevel(Routes.BACKUP) }
                        )
                    }

                    composable(
                        route = Routes.STUDIO,
                        enterTransition = tabEnter,
                        exitTransition = tabExit,
                        popEnterTransition = tabEnter,
                        popExitTransition = tabExit
                    ) {
                        SongsListScreen(
                            songs = allSongs,
                            totalVaultIdeas = allIdeas.size,
                            onOpenSong = { id ->
                                navController.navigate(Routes.songwriting(songId = id))
                            },
                            onCreateNewSong = {
                                navController.navigate(Routes.songwriting(songId = 0L))
                            }
                        )
                    }

                    composable(
                        route = Routes.BACKUP,
                        enterTransition = tabEnter,
                        exitTransition = tabExit,
                        popEnterTransition = tabEnter,
                        popExitTransition = tabExit
                    ) {
                        BackupSettingsScreen(
                            totalIdeas = allIdeas.size,
                            unusedIdeas = unusedCount,
                            totalSongs = allSongs.size,
                            totalAudioClips = audioClipsCount,
                            backupState = backupState,
                            themeMode = themeMode,
                            onExportJson = viewModel::exportVaultJson,
                            onImportFile = viewModel::importVaultFile,
                            onBulkImportText = viewModel::bulkImportText,
                            onThemeModeChange = viewModel::setThemeMode
                        )
                    }

                    composable(
                        route = Routes.IDEA_EDITOR,
                        arguments = listOf(
                            navArgument("ideaId") { type = NavType.LongType },
                            navArgument("autoRecord") { type = NavType.BoolType }
                        ),
                        enterTransition = editorEnter,
                        exitTransition = editorExit,
                        popEnterTransition = editorPopEnter,
                        popExitTransition = editorPopExit
                    ) { entry ->
                        val id = entry.arguments?.getLong("ideaId") ?: 0L
                        val autoRecord = entry.arguments?.getBoolean("autoRecord") ?: false
                        IdeaEditorScreen(
                            ideaId = if (id > 0L) id else null,
                            startRecordingImmediately = autoRecord,
                            viewModel = viewModel,
                            recordingState = recordingState,
                            playbackState = playbackState,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = Routes.SONGWRITING,
                        arguments = listOf(
                            navArgument("songId") { type = NavType.LongType }
                        ),
                        enterTransition = editorEnter,
                        exitTransition = editorExit,
                        popEnterTransition = editorPopEnter,
                        popExitTransition = editorPopExit
                    ) { entry ->
                        val id = entry.arguments?.getLong("songId") ?: 0L
                        SongwritingScreen(
                            songId = if (id > 0L) id else null,
                            allIdeas = allIdeas,
                            playbackState = playbackState,
                            viewModel = viewModel,
                            onOpenIdea = { ideaId ->
                                navController.navigate(Routes.ideaEditor(ideaId = ideaId, autoRecord = false))
                            },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
