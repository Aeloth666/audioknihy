package cz.audioknihy.auto

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import cz.audioknihy.library.Book
import cz.audioknihy.progress.BookProgress
import java.text.Collator
import java.util.Locale

/** Strom procházení pro Android Auto: Rozposlouchané / Knihovna → autoři → knihy. */
object AutoTree {
    const val ROOT = "root"
    const val IN_PROGRESS = "inprogress"
    const val LIBRARY = "library"
    private const val AUTHOR_PREFIX = "author:"
    private const val BOOK_PREFIX = "book:"

    data class Labels(val inProgress: String, val library: String)

    private val collator = Collator.getInstance(Locale("cs", "CZ"))

    fun authorId(author: String) = AUTHOR_PREFIX + author
    fun bookMediaId(id: String) = BOOK_PREFIX + id
    fun bookIdOf(mediaId: String): String? = mediaId.takeIf { it.startsWith(BOOK_PREFIX) }?.removePrefix(BOOK_PREFIX)

    fun root(labels: Labels): MediaItem = folder(ROOT, "Audioknihy")

    fun children(
        parentId: String,
        books: List<Book>,
        progress: List<BookProgress>,
        labels: Labels,
        artwork: (Book) -> String? = { null },
    ): List<MediaItem> = when {
        parentId == ROOT -> listOf(folder(IN_PROGRESS, labels.inProgress), folder(LIBRARY, labels.library))
        parentId == IN_PROGRESS -> {
            val byId = books.associateBy { it.id }
            progress.filter { !it.finished }.sortedByDescending { it.lastPlayedAt }
                .mapNotNull { byId[it.bookId] }.map { bookItem(it, artwork(it)) }
        }
        parentId == LIBRARY -> books.map { it.author }.distinct().sortedWith(collator)
            .map { folder(authorId(it), it) }
        parentId.startsWith(AUTHOR_PREFIX) -> {
            val author = parentId.removePrefix(AUTHOR_PREFIX)
            books.filter { it.author == author }.sortedWith { a, b -> collator.compare(a.title, b.title) }
                .map { bookItem(it, artwork(it)) }
        }
        else -> emptyList()
    }

    private fun folder(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS)
                .build()
        )
        .build()

    fun bookItem(book: Book, artworkUri: String?) = MediaItem.Builder()
        .setMediaId(bookMediaId(book.id))
        .setUri(Uri.parse("audioknihy://book"))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(book.title)
                .setArtist(book.author)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setArtworkUri(artworkUri?.let(Uri::parse))
                .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                .build()
        )
        .build()
}
