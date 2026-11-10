package com.automattic.simplenote.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automattic.simplenote.R
import com.automattic.simplenote.utils.DisplayUtils
import com.automattic.simplenote.viewmodels.CollaboratorsViewModel
import com.automattic.simplenote.viewmodels.CollaboratorsViewModel.UiState

// The View world's padding_list_bottom: keeps the last row clear of the FAB.
private val ListBottomPadding = 88.dp

// The View toolbar pinned contentInsetStart to toolbar_title_keyline (72dp); the M3 TopAppBar
// places the title right after the 48dp navigation slot + 4dp window padding, so 20dp closes
// the gap to the keyline.
private val TitleKeylinePadding = 20.dp

/**
 * Compose port of the Collaborators screen, driven by the frozen [CollaboratorsViewModel].
 *
 * Rendering is a pure function of [CollaboratorsViewModel.uiState] (consumed via
 * `observeAsState()`); clicks call straight into the same ViewModel entry points the View world
 * used. Events ([CollaboratorsViewModel.event]) stay observed in [CollaboratorsActivity], which
 * owns the toasts, the finish/navigate calls, and the two dialogs' visibility.
 *
 * Insets: the hosting activity still goes edge to edge through ThemedAppCompatActivity
 * (SystemBarUtils.applyEdgeToEdge); the Scaffold consumes [WindowInsets.safeDrawing], which
 * reproduces the SystemBarUtils.applyInsets wiring the View layout needed — top bar below the
 * status bar, content and FAB clear of the navigation bar and the IME, cutout insets absorbed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollaboratorsScreen(viewModel: CollaboratorsViewModel) {
    val uiState by viewModel.uiState.observeAsState()
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Mirrors the View world's updateSearchVisibility: the search action hides only when the
    // note genuinely has no collaborators (not when a search produced no matches).
    val searchVisible = when (val state = uiState) {
        is UiState.EmptyCollaborators -> !state.allCollaboratorsRemoved
        else -> true
    }

    fun collapseSearch() {
        searchExpanded = false
        searchQuery = ""
        viewModel.closeSearch()
    }

    // The collapsible SearchView action view consumed back presses while expanded.
    BackHandler(enabled = searchExpanded) { collapseSearch() }

    // scrollToPosition(0) on search-driven updates, as the adapter submitList callback did.
    LaunchedEffect(uiState) {
        val searchUpdate = when (val state = uiState) {
            is UiState.CollaboratorsList -> state.searchUpdate
            is UiState.EmptyCollaborators -> state.searchUpdate
            else -> false
        }
        if (searchUpdate) {
            listState.scrollToItem(0)
        }
        // Removing the last collaborator resets the ViewModel's query and hid the search menu
        // item, which collapsed the expanded SearchView; fold the field away the same way.
        if (!searchVisible && searchExpanded) {
            searchExpanded = false
            searchQuery = ""
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        // Every app style pins colorSurface to the window background this screen used to show.
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CollaboratorsTopBar(
                searchVisible = searchVisible,
                searchExpanded = searchExpanded,
                searchQuery = searchQuery,
                onSearchQueryChange = { query ->
                    searchQuery = query
                    viewModel.search(query)
                },
                onSearchExpand = { searchExpanded = true },
                onNavigateBack = {
                    if (searchExpanded) {
                        collapseSearch()
                    } else {
                        viewModel.close()
                    }
                },
            )
        },
        floatingActionButton = {
            AddCollaboratorFab(
                onClick = viewModel::clickAddCollaborator,
                onLongClick = viewModel::longClickAddCollaborator,
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is UiState.CollaboratorsList -> CollaboratorsList(
                collaborators = state.collaborators,
                listState = listState,
                innerPadding = innerPadding,
                onRemoveClick = viewModel::clickRemoveCollaborator,
                onRemoveLongClick = viewModel::longClickRemoveCollaborator,
            )
            is UiState.EmptyCollaborators -> EmptyView(
                searchEmpty = !state.allCollaboratorsRemoved,
                modifier = Modifier.padding(innerPadding),
            )
            // NoteDeleted / NoteInTrash / initial null state: the activity toasts and navigates
            // away; the View world briefly kept the last adapter contents, which were empty here.
            else -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            )
        }
    }
}

/**
 * Remove-collaborator confirmation, previously an AlertDialog.Builder block in the activity.
 * Strings are unchanged: title Remove Collaborator, message, Cancel / Remove buttons.
 */
@Composable
fun RemoveCollaboratorDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remove_collaborator)) },
        text = { Text(stringResource(R.string.remove_collaborator_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollaboratorsTopBar(
    searchVisible: Boolean,
    searchExpanded: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchExpand: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val toolbarIconColor = LocalSimplenoteColors.current.toolbarIcon
    TopAppBar(
        title = {
            if (searchExpanded) {
                SearchField(query = searchQuery, onQueryChange = onSearchQueryChange)
            } else {
                Text(
                    text = stringResource(R.string.collaborators),
                    modifier = Modifier.padding(start = TitleKeylinePadding),
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_left_24dp),
                    contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                    tint = toolbarIconColor,
                )
            }
        },
        actions = {
            if (searchVisible && !searchExpanded) {
                IconButton(onClick = onSearchExpand) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search_24dp),
                        contentDescription = stringResource(R.string.search_collaborators),
                        tint = toolbarIconColor,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            // The deleted toolbar include drew on colorSurfaceContainerLow.
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        placeholder = {
            Text(
                text = stringResource(R.string.search_collaborators_hint),
                // The SearchView hint was force-colored text_title_disabled via HtmlCompat.
                color = colorResource(R.color.text_title_disabled),
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cross_24dp),
                        contentDescription = stringResource(
                            androidx.appcompat.R.string.abc_searchview_description_clear
                        ),
                        tint = LocalSimplenoteColors.current.toolbarIcon,
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun CollaboratorsList(
    collaborators: List<String>,
    listState: LazyListState,
    innerPadding: PaddingValues,
    onRemoveClick: (String) -> Unit,
    onRemoveLongClick: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // The View list scrolled edge to edge behind the navigation bar (clipToPadding=false)
        // with a padding_list_bottom floor so the last row clears the FAB; innerPadding carries
        // the system bar and IME insets the old applyInsets listener applied.
        contentPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection),
            top = innerPadding.calculateTopPadding(),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding() + ListBottomPadding,
        ),
    ) {
        item(key = "collaborators_header") { SharedWithHeader() }
        // Index-qualified keys: a synced note can carry exact duplicate emails (the repository
        // does not dedupe), which the old DiffUtil adapter tolerated — a bare email key would
        // crash LazyColumn with an IllegalArgumentException.
        itemsIndexed(items = collaborators, key = { index, collaborator -> "$index:$collaborator" }) { _, collaborator ->
            CollaboratorRow(
                collaborator = collaborator,
                onRemoveClick = onRemoveClick,
                onRemoveLongClick = onRemoveLongClick,
            )
        }
    }
}

@Composable
private fun SharedWithHeader() {
    Text(
        text = stringResource(R.string.shared_with),
        modifier = Modifier.padding(start = 16.dp, top = 22.dp, end = 8.dp, bottom = 12.dp),
        // collaborators_header used colorAccent, which every app style pins to colorPrimary.
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        lineHeight = 20.sp, // 14sp + the 6sp lineSpacingExtra of the View header
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollaboratorRow(
    collaborator: String,
    onRemoveClick: (String) -> Unit,
    onRemoveLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = collaborator,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
        )
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .size(48.dp) // the 24dp ic_cross plus the ImageButton's padding_medium all around
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    // actionBarItemBackground in the View row: an unbounded ripple.
                    indication = ripple(bounded = false, radius = 24.dp),
                    role = Role.Button,
                    onLongClick = onRemoveLongClick,
                    onClick = { onRemoveClick(collaborator) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cross_24dp),
                contentDescription = stringResource(R.string.remove_collaborator),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyView(searchEmpty: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Read so the landscape check re-evaluates when the configuration changes.
    LocalConfiguration.current
    val hideImage = DisplayUtils.isLandscape(context) && !DisplayUtils.isLargeScreen(context)
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!hideImage) {
            Icon(
                painter = painterResource(
                    if (searchEmpty) R.drawable.ic_search_24dp else R.drawable.ic_collaborate_24dp
                ),
                contentDescription = null,
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .size(72.dp), // icon_empty
                tint = LocalSimplenoteColors.current.emptyImage,
            )
        }
        Text(
            text = stringResource(
                if (searchEmpty) R.string.no_collaborators_search else R.string.no_collaborators
            ),
            modifier = Modifier
                .widthIn(max = 312.dp) // empty_width_maximum
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 20.sp, // text_empty
            textAlign = TextAlign.Center,
        )
        if (!searchEmpty) {
            // The search-empty variant blanked this message (setEmptyListMessage("")).
            Text(
                text = stringResource(R.string.add_email_collaborator_message),
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp, // text_empty_button
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddCollaboratorFab(onClick: () -> Unit, onLongClick: () -> Unit) {
    // M3's FloatingActionButton composable has no long-press slot, so this rebuilds it as a
    // Surface + combinedClickable with the colors Widget.Simplenote.Fab pinned (primary/onPrimary)
    // and the default M3 FAB shape and elevation.
    Surface(
        shape = FloatingActionButtonDefaults.shape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp,
    ) {
        Box(
            modifier = Modifier
                .combinedClickable(
                    role = Role.Button,
                    onLongClick = onLongClick,
                    onClick = onClick,
                )
                .size(56.dp), // button_floating
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add_24dp),
                // The View layout reused add_tag as the FAB's contentDescription; kept identical.
                contentDescription = stringResource(R.string.add_tag),
            )
        }
    }
}
