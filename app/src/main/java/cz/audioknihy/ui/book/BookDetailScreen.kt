package cz.audioknihy.ui.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.PlayArrow
import cz.audioknihy.ui.CoverWithRibbon
import cz.audioknihy.ui.CoverZoomDialog
import cz.audioknihy.ui.rememberCoverUrl
import cz.audioknihy.ui.Ribbon
import cz.audioknihy.ui.theme.Silk
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cz.audioknihy.R
import cz.audioknihy.chapters.Chapter
import cz.audioknihy.chapters.ChapterRepository
import cz.audioknihy.library.Book
import cz.audioknihy.library.LibraryRepository
import cz.audioknihy.playback.BookSession
import cz.audioknihy.playback.StartAt
import cz.audioknihy.plex.ServerSession
import cz.audioknihy.progress.BookProgress
import cz.audioknihy.progress.ProgressDao
import cz.audioknihy.ui.Cover
import cz.audioknihy.ui.TimeFormat
import cz.audioknihy.ui.durationText
import cz.audioknihy.ui.player.MiniPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val library: LibraryRepository,
    private val chapterRepo: ChapterRepository,
    private val session: BookSession,
    private val server: ServerSession,
    progressDao: ProgressDao,
) : ViewModel() {
    private val bookId: String = checkNotNull(savedState["id"])
    val book = MutableStateFlow<Book?>(null)
    val chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val progress: StateFlow<BookProgress?> = progressDao.all().map { all -> all.firstOrNull { it.bookId == bookId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val b = library.book(bookId) ?: return@launch
            book.value = b
            chapters.value = chapterRepo.chapters(b).chapters
        }
    }

    fun artwork(thumb: String?) = server.imageUrl(thumb, 600)

    fun play(startAt: StartAt) = viewModelScope.launch { book.value?.let { session.open(it, startAt) } }

    fun setUnplayed() = viewModelScope.launch { book.value?.let { session.setUnplayed(it) } }

    fun setFinished(finished: Boolean) = viewModelScope.launch { book.value?.let { session.setFinished(it, finished) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(onBack: () -> Unit, onOpenPlayer: () -> Unit, vm: BookDetailViewModel = hiltViewModel()) {
    val book by vm.book.collectAsStateWithLifecycle()
    val chapters by vm.chapters.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(false) }
    book?.let { b -> if (picker) CoverPickerDialog(b) { picker = false } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.author.orEmpty()) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.find_cover)) }, onClick = { menu = false; picker = true })
                        DropdownMenuItem(
                            text = { Text(stringResource(if (progress?.finished == true) R.string.mark_unfinished else R.string.mark_finished)) },
                            onClick = { menu = false; vm.setFinished(progress?.finished != true) },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.mark_unplayed)) }, onClick = { menu = false; vm.setUnplayed() })
                    }
                },
            )
        },
        bottomBar = { MiniPlayer(onOpen = onOpenPlayer) },
    ) { pad ->
        val b = book ?: return@Scaffold
        val finished = progress?.finished == true
        LazyColumn(Modifier.padding(pad)) {
            item {
                val bookFraction = progress?.let { p ->
                    if (p.finished) 1f else if (p.lastPlayedAt <= 0 || b.durationMs <= 0) 0f
                    else (b.files.take(p.fileIndex).sumOf { it.durationMs } + p.positionMs).toFloat() / b.durationMs
                } ?: 0f
                Row(Modifier.padding(16.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val coverUrl = rememberCoverUrl(b)
                    var zoom by remember { mutableStateOf(false) }
                    if (zoom) CoverZoomDialog(coverUrl) { zoom = false }
                    CoverWithRibbon(
                        coverUrl, bookFraction, Silk.forBook(b.id), Modifier.padding(end = 4.dp), width = 124.dp,
                        maxHeight = 186.dp, onClick = { zoom = true },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(b.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(durationText(b.durationMs), style = MaterialTheme.typography.bodyMedium)
                        if (finished) Text(stringResource(R.string.finished_badge), color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.play(StartAt.Saved); onOpenPlayer() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Text(" " + stringResource(R.string.continue_listening))
                    }
                    OutlinedButton(onClick = { vm.play(StartAt.Beginning); onOpenPlayer() }, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.from_start))
                    }
                }
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = { vm.setFinished(!finished) }) {
                        Text(stringResource(if (finished) R.string.mark_unfinished else R.string.mark_finished))
                    }
                    val started = progress?.let { it.finished || it.lastPlayedAt > 0 } == true
                    if (started) TextButton(onClick = { vm.setUnplayed() }) { Text(stringResource(R.string.mark_unplayed)) }
                }
                Text(
                    stringResource(R.string.chapters),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            itemsIndexed(chapters) { i, ch ->
                val current = progress?.let { p -> !p.finished && p.fileIndex == ch.fileIndex && p.positionMs in ch.startMs until ch.endMs } == true
                ListItem(
                    leadingContent = {
                        Box(Modifier.width(12.dp).height(28.dp)) {
                            if (current) Ribbon(1f, Silk.forBook(b.id), Modifier.height(28.dp), width = 10.dp)
                        }
                    },
                    headlineContent = {
                        Text(
                            if (ch.title.firstOrNull()?.isDigit() == true) ch.title else "${i + 1}. ${ch.title}",
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = { Text(TimeFormat.clock(ch.endMs - ch.startMs), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { vm.play(StartAt.AtChapter(ch)); onOpenPlayer() },
                )
            }
        }
    }
}
