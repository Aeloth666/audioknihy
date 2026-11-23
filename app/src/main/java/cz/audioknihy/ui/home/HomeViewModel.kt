package cz.audioknihy.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.audioknihy.library.Book
import cz.audioknihy.library.LibraryRepository
import cz.audioknihy.playback.BookSession
import cz.audioknihy.plex.ServerSession
import cz.audioknihy.progress.ProgressDao
import cz.audioknihy.settings.Settings
import cz.audioknihy.ui.library.LibrarySearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val progress: ProgressDao,
    private val session: BookSession,
    private val server: ServerSession,
    private val settings: Settings,
) : ViewModel() {

    val sort: StateFlow<InProgressSort> = settings.inProgressSort
        .map { v -> InProgressSort.entries.firstOrNull { it.name == v } ?: InProgressSort.RECENT }
        .stateIn(viewModelScope, SharingStarted.Eagerly, InProgressSort.RECENT)

    val rows: StateFlow<List<InProgressRow>> = combine(progress.inProgress(), library.books, sort) { p, b, o ->
        InProgressRows.sort(InProgressRows.build(p, b), o)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Poslechnutý podíl knih, které jsou rozposlouchané (pro stužky v knihovně). */
    val progressById: StateFlow<Map<String, Float>> = combine(progress.all(), library.books) { p, b ->
        val inProgress = InProgressRows.build(p.filter { !it.finished && it.lastPlayedAt > 0 }, b).associate { it.book.id to it.fraction }
        inProgress + p.filter { it.finished }.associate { it.bookId to 1f }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setSort(order: InProgressSort) = viewModelScope.launch { settings.setInProgressSort(order.name) }

    val query = MutableStateFlow("")

    val libraryBooks: StateFlow<List<Book>> = combine(library.books, query) { b, q -> LibrarySearch.filter(b, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val finishedIds: StateFlow<Set<String>> = combine(progress.all(), library.books) { p, _ ->
        p.filter { it.finished }.map { it.bookId }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _errors = Channel<Unit>(Channel.CONFLATED)
    val errors = _errors.receiveAsFlow()

    /** Signál, že lze sestavit adresy obalů. */
    val artReady: StateFlow<Int> = server.ready

    init {
        viewModelScope.launch { server.client() }
        refresh()
    }

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            val ok = server.ensureReachable() && library.refresh().isSuccess
            if (!ok) _errors.trySend(Unit)
            _refreshing.value = false
        }
    }

    fun artwork(thumb: String?): String? = server.imageUrl(thumb, 300)

    fun play(book: Book) = viewModelScope.launch { session.open(book) }

    fun setUnplayed(book: Book) = viewModelScope.launch { session.setUnplayed(book) }

    fun setFinished(book: Book, finished: Boolean) = viewModelScope.launch { session.setFinished(book, finished) }
}

private fun <T> MutableStateFlow<T>.asStateFlow(): StateFlow<T> = this
