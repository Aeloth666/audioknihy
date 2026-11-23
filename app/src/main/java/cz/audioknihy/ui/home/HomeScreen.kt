package cz.audioknihy.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.draw.rotate
import cz.audioknihy.ui.CoverWithRibbon
import cz.audioknihy.ui.rememberCoverUrl
import cz.audioknihy.ui.Ribbon
import cz.audioknihy.ui.theme.Silk
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.audioknihy.R
import cz.audioknihy.library.Book
import cz.audioknihy.ui.Cover
import cz.audioknihy.ui.durationText
import cz.audioknihy.ui.player.MiniPlayer
import cz.audioknihy.ui.remainingText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPlayer: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenSettings: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        vm.errors.collect { snackbar.showSnackbar(ctx.getString(R.string.server_unreachable)) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = vm::refresh, enabled = !refreshing) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
                },
            )
        },
        bottomBar = { MiniPlayer(onOpen = onOpenPlayer) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.in_progress)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.library)) })
            }
            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (tab) {
                0 -> InProgressList(vm, onPlay = { vm.play(it); onOpenPlayer() }, onOpenBook = onOpenBook)
                else -> LibraryList(vm, onOpenBook = onOpenBook)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InProgressList(vm: HomeViewModel, onPlay: (Book) -> Unit, onOpenBook: (String) -> Unit) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    vm.artReady.collectAsStateWithLifecycle().value // překreslit, až budou adresy obalů
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.empty_in_progress), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "sort") { SortBar(sort, vm::setSort) }
        items(rows, key = { it.book.id }) { row ->
            var menu by remember { mutableStateOf(false) }
            val silk = Silk.forBook(row.book.id)
            Box(Modifier.animateItem()) {
                Row(
                    Modifier.fillMaxWidth()
                        .combinedClickable(onClick = { onOpenBook(row.book.id) }, onLongClick = { menu = true })
                        .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverWithRibbon(
                        url = rememberCoverUrl(row.book, 300),
                        fraction = row.fraction,
                        color = silk,
                        modifier = Modifier.padding(end = 14.dp),
                        width = 84.dp,
                        maxHeight = 124.dp,
                        description = stringResource(R.string.progress_percent, (row.fraction * 100).toInt()),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(row.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            row.book.author, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.part_of, row.partNumber, row.partCount) + "  ·  " + remainingText(row.remainingMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { onPlay(row.book) },
                        modifier = Modifier.padding(start = 8.dp).size(56.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(Icons.Filled.PlayArrow, stringResource(R.string.continue_listening), Modifier.size(32.dp))
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.continue_listening)) }, onClick = { menu = false; onPlay(row.book) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.chapters)) }, onClick = { menu = false; onOpenBook(row.book.id) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.mark_finished)) }, onClick = { menu = false; vm.setFinished(row.book, true) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.mark_unplayed)) }, onClick = { menu = false; vm.setUnplayed(row.book) })
                }
            }
        }
    }
}

@Composable
private fun SortBar(sort: InProgressSort, onPick: (InProgressSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val labels = mapOf(
        InProgressSort.RECENT to R.string.sort_recent,
        InProgressSort.TITLE to R.string.sort_title,
        InProgressSort.AUTHOR to R.string.sort_author,
        InProgressSort.LEAST_REMAINING to R.string.sort_least_remaining,
        InProgressSort.MOST_PROGRESS to R.string.sort_most_progress,
    )
    Box(Modifier.padding(start = 16.dp, top = 8.dp)) {
        AssistChip(
            onClick = { open = true },
            label = { Text(stringResource(R.string.sort_by) + ": " + stringResource(labels.getValue(sort))) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp)) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            labels.forEach { (order, res) ->
                DropdownMenuItem(
                    text = { Text(stringResource(res)) },
                    onClick = { open = false; onPick(order) },
                    trailingIcon = { if (order == sort) Icon(Icons.Filled.Check, null) },
                )
            }
        }
    }
}

@Composable
private fun LibraryList(vm: HomeViewModel, onOpenBook: (String) -> Unit) {
    val books by vm.libraryBooks.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val progress by vm.progressById.collectAsStateWithLifecycle()
    vm.artReady.collectAsStateWithLifecycle().value // překreslit, až budou adresy obalů
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (books.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.empty_library), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        val grouped = books.groupBy { it.author }
        val expanded = remember { mutableStateMapOf<String, Boolean>() }
        val searching = query.isNotBlank()
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            grouped.forEach { (author, list) ->
                val open = searching || expanded[author] == true
                item(key = "a:$author") {
                    val startedBooks = list.filter { (progress[it.id] ?: 0f) > 0f }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .clickable(enabled = !searching) { expanded[author] = !open }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(author.ifBlank { "?" }, style = MaterialTheme.typography.titleMedium)
                            Text(
                                pluralStringResource(R.plurals.books_count, list.size, list.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(end = 12.dp)) {
                            startedBooks.take(5).forEach { b ->
                                Ribbon(1f, Silk.forBook(b.id), Modifier.height(22.dp), width = 6.dp)
                            }
                        }
                        val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
                        Icon(
                            Icons.Filled.ExpandMore,
                            contentDescription = stringResource(if (open) R.string.collapse else R.string.expand),
                            modifier = Modifier.rotate(turn),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (open) {
                    items(list, key = { it.id }) { book ->
                        val f = progress[book.id] ?: 0f
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenBook(book.id) }
                                .padding(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoverWithRibbon(rememberCoverUrl(book, 200), f, Silk.forBook(book.id), Modifier.padding(end = 12.dp), width = 54.dp, maxHeight = 80.dp)
                            Column(Modifier.weight(1f)) {
                                Text(book.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    durationText(book.durationMs) + if (f >= 1f) "  ·  " + stringResource(R.string.finished_badge) else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
