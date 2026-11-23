package cz.audioknihy.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.audioknihy.R
import cz.audioknihy.library.Book
import cz.audioknihy.ui.CoverWithRibbon
import cz.audioknihy.ui.durationText
import cz.audioknihy.ui.rememberCoverUrl
import cz.audioknihy.ui.remainingText
import cz.audioknihy.ui.theme.Silk

/** Police: rozposlouchané jako mřížka obalů se stužkami. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InProgressShelf(vm: HomeViewModel, onPlay: (Book) -> Unit, onOpenBook: (String) -> Unit) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    if (rows.isEmpty()) return EmptyText(R.string.empty_in_progress)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { SortBar(sort, vm::setSort) }
        items(rows, key = { it.book.id }) { row ->
            var menu by remember { mutableStateOf(false) }
            Column(
                Modifier.animateItem().combinedClickable(onClick = { onOpenBook(row.book.id) }, onLongClick = { menu = true }),
            ) {
                Box {
                    CoverWithRibbon(
                        rememberCoverUrl(row.book, 400), row.fraction, Silk.forBook(row.book.id),
                        Modifier.padding(top = 6.dp), width = 160.dp, maxHeight = 220.dp,
                    )
                    FilledIconButton(
                        onClick = { onPlay(row.book) },
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).size(52.dp),
                    ) { Icon(Icons.Filled.PlayArrow, stringResource(R.string.continue_listening), Modifier.size(30.dp)) }
                    BookMenu(menu, { menu = false }, vm, row.book, onPlay, onOpenBook)
                }
                Text(
                    row.book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(remainingText(row.remainingMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Kompakt: jen text a tenká linka průběhu; velké písmo, nic nesvítí. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InProgressCompact(vm: HomeViewModel, onPlay: (Book) -> Unit, onOpenBook: (String) -> Unit) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    if (rows.isEmpty()) return EmptyText(R.string.empty_in_progress)
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "sort") { SortBar(sort, vm::setSort) }
        items(rows, key = { it.book.id }) { row ->
            var menu by remember { mutableStateOf(false) }
            val silk = Silk.forBook(row.book.id)
            Box(Modifier.animateItem()) {
                Row(
                    Modifier.fillMaxWidth()
                        .combinedClickable(onClick = { onOpenBook(row.book.id) }, onLongClick = { menu = true })
                        .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(row.book.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            row.book.author + "  ·  " + remainingText(row.remainingMs),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        LinearProgressIndicator(
                            progress = { row.fraction },
                            color = silk,
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(3.dp),
                        )
                    }
                    IconButton(onClick = { onPlay(row.book) }, modifier = Modifier.padding(start = 8.dp).size(64.dp)) {
                        Icon(Icons.Filled.PlayArrow, stringResource(R.string.continue_listening), Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                BookMenu(menu, { menu = false }, vm, row.book, onPlay, onOpenBook)
            }
            HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Police: knihovna jako police, každý autor má řadu obalů. */
@Composable
internal fun LibraryShelf(vm: HomeViewModel, onOpenBook: (String) -> Unit) {
    val books by vm.libraryBooks.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val progress by vm.progressById.collectAsStateWithLifecycle()
    Column {
        SearchField(query) { vm.query.value = it }
        if (books.isEmpty()) return@Column EmptyText(R.string.empty_library)
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            books.groupBy { it.author }.forEach { (author, list) ->
                item(key = "a:$author") {
                    Column(Modifier.padding(top = 12.dp)) {
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Bottom) {
                            Text(author.ifBlank { "?" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(
                                pluralStringResource(R.plurals.books_count, list.size, list.size),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(list, key = { it.id }) { book ->
                                Column(Modifier.width(108.dp).clickable { onOpenBook(book.id) }) {
                                    CoverWithRibbon(
                                        rememberCoverUrl(book, 300), progress[book.id] ?: 0f, Silk.forBook(book.id),
                                        Modifier.padding(top = 6.dp), width = 108.dp, maxHeight = 150.dp,
                                    )
                                    Text(
                                        book.title, style = MaterialTheme.typography.labelLarge, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
                                    )
                                    Text(durationText(book.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        // hrana police
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 3.dp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
internal fun EmptyText(res: Int) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(res), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun BookMenu(
    open: Boolean, onDismiss: () -> Unit, vm: HomeViewModel, book: Book,
    onPlay: (Book) -> Unit, onOpenBook: (String) -> Unit,
) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text(stringResource(R.string.continue_listening)) }, onClick = { onDismiss(); onPlay(book) })
        DropdownMenuItem(text = { Text(stringResource(R.string.chapters)) }, onClick = { onDismiss(); onOpenBook(book.id) })
        DropdownMenuItem(text = { Text(stringResource(R.string.mark_finished)) }, onClick = { onDismiss(); vm.setFinished(book, true) })
        DropdownMenuItem(text = { Text(stringResource(R.string.mark_unplayed)) }, onClick = { onDismiss(); vm.setUnplayed(book) })
    }
}

@Suppress("unused")
private val keepSpacer: @Composable () -> Unit = { Spacer(Modifier.heightIn(min = 0.dp)) }
