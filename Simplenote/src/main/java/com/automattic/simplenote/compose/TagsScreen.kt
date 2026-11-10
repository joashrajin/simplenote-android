package com.automattic.simplenote.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import com.automattic.simplenote.models.TagItem
import com.automattic.simplenote.utils.DisplayUtils
import com.automattic.simplenote.viewmodels.TagsViewModel

// The View world's padding_list_bottom: keeps the last row clear of the FAB.
private val ListBottomPadding = 88.dp

// The View toolbar pinned contentInsetStart to toolbar_title_keyline (72dp); the M3 TopAppBar
// places the title right after the 48dp navigation slot + 4dp window padding, so 20dp closes
// the gap to the keyline.
private val TitleKeylinePadding = 20.dp

/**
 * Compose port of the Tags screen, driven by the frozen [TagsViewModel] (whose single sanctioned
 * change replaced the View payload of LongDeleteTagEvent with the tapped [TagItem]).
 *
 * Rendering is a pure function of [TagsViewModel.uiState] (consumed via `observeAsState()`);
 * clicks call straight into the same ViewModel entry points the View world used. Events
 * ([TagsViewModel.event]) stay observed in [com.automattic.simplenote.TagsActivity], which owns
 * the toasts, finish, AddTagActivity navigation, the rename DialogFragment, and the
 * delete-confirmation dialog's visibility.
 *
 * Insets: the hosting activity still goes edge to edge through ThemedAppCompatActivity
 * (SystemBarUtils.applyEdgeToEdge); the Scaffold consumes [WindowInsets.safeDrawing], which
 * reproduces the SystemBarUtils.applyInsets + FAB inset-listener wiring the View layout needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(viewModel: TagsViewModel) {
    val uiState by viewModel.uiState.observeAsState()
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    fun collapseSearch() {
        searchExpanded = false
        searchQuery = ""
        // The frozen closeSearch() only clears the query (the View world relied on the collapsing
        // SearchView firing one last onQueryTextChange("")); updateOnResult() then re-queries with
        // that cleared query, restoring the full list.
        viewModel.closeSearch()
        viewModel.updateOnResult()
    }

    // The collapsible SearchView action view consumed back presses while expanded.
    BackHandler(enabled = searchExpanded) { collapseSearch() }

    // scrollToPosition(0) on search-driven updates, as the adapter submitList callback did.
    LaunchedEffect(uiState) {
        if (uiState?.searchUpdate == true) {
            listState.scrollToItem(0)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        // Every app style pins colorSurface to the window background this screen used to show.
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TagsTopBar(
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
            AddTagFab(
                onClick = viewModel::clickAddTag,
                onLongClick = viewModel::longClickAddTag,
            )
        },
    ) { innerPadding ->
        val state = uiState
        when {
            // Initial null state: the first UiState lands when start()'s coroutine completes; the
            // View world's RecyclerView also started empty with the empty view visible.
            state == null || state.tagItems.isEmpty() -> EmptyView(
                searchEmpty = state?.searchQuery != null,
                modifier = Modifier.padding(innerPadding),
            )
            else -> TagsList(
                tagItems = state.tagItems,
                listState = listState,
                innerPadding = innerPadding,
                onEditClick = viewModel::clickEditTag,
                onDeleteClick = viewModel::clickDeleteTag,
                onDeleteLongClick = viewModel::longClickDeleteTag,
            )
        }
    }
}

/**
 * Delete-tag confirmation, previously a MaterialAlertDialogBuilder block in the activity.
 * Strings are unchanged: title Delete Tag, message, Yes / No buttons.
 */
@Composable
fun DeleteTagDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_tag)) },
        text = { Text(stringResource(R.string.confirm_delete_tag)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.no))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagsTopBar(
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
                    text = stringResource(R.string.edit_tags),
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
            // The View menu item was always shown (showAsAction = always|collapseActionView).
            if (!searchExpanded) {
                IconButton(onClick = onSearchExpand) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search_24dp),
                        contentDescription = stringResource(R.string.search_tags),
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
                text = stringResource(R.string.search_tags_hint),
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
private fun TagsList(
    tagItems: List<TagItem>,
    listState: LazyListState,
    innerPadding: PaddingValues,
    onEditClick: (TagItem) -> Unit,
    onDeleteClick: (TagItem) -> Unit,
    onDeleteLongClick: (TagItem) -> Unit,
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
        // Simperium keys are the bucket's primary keys, so they are unique — the old DiffUtil
        // callback used the same identity.
        items(items = tagItems, key = { it.tag.simperiumKey }) { tagItem ->
            TagRow(
                tagItem = tagItem,
                onEditClick = onEditClick,
                onDeleteClick = onDeleteClick,
                onDeleteLongClick = onDeleteLongClick,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagRow(
    tagItem: TagItem,
    onEditClick: (TagItem) -> Unit,
    onDeleteClick: (TagItem) -> Unit,
    onDeleteLongClick: (TagItem) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp) // minimum_target
            .clickable { onEditClick(tagItem) }, // selectableItemBackground on the View row
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = tagItem.tag.name,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp), // padding_large / padding_small
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp, // textAppearanceMedium
        )
        if (tagItem.noteCount > 0) {
            Text(
                text = tagItem.noteCount.toString(),
                modifier = Modifier.padding(end = 8.dp), // padding_small
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp, // textAppearanceMedium
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp) // the 24dp ic_trash plus the ImageButton's padding_medium all around
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    // actionBarItemBackground in the View row: an unbounded ripple.
                    indication = ripple(bounded = false, radius = 24.dp),
                    role = Role.Button,
                    onLongClick = { onDeleteLongClick(tagItem) },
                    onClick = { onDeleteClick(tagItem) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_trash_24dp),
                contentDescription = stringResource(R.string.delete_tag),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmptyView(searchEmpty: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Read so the landscape check re-evaluates when the configuration changes.
    LocalConfiguration.current
    // Only the search-results variant hid its image in small landscape; the default kept it.
    val hideImage = searchEmpty &&
        DisplayUtils.isLandscape(context) &&
        !DisplayUtils.isLargeScreen(context)
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!hideImage) {
            Icon(
                painter = painterResource(
                    if (searchEmpty) R.drawable.ic_search_24dp else R.drawable.ic_tag_24dp
                ),
                contentDescription = null,
                modifier = Modifier
                    .padding(vertical = 16.dp) // padding_large
                    .size(72.dp), // icon_empty
                tint = LocalSimplenoteColors.current.emptyImage,
            )
        }
        Text(
            text = stringResource(if (searchEmpty) R.string.empty_tags_search else R.string.empty_tags),
            modifier = Modifier
                .widthIn(max = 312.dp) // empty_width_maximum
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 20.sp, // text_empty
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddTagFab(onClick: () -> Unit, onLongClick: () -> Unit) {
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
                contentDescription = stringResource(R.string.add_tag),
            )
        }
    }
}
