package cz.audioknihy.progress

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Uložená pozice jedné knihy. Místní databáze je zdroj pravdy. */
@Entity(tableName = "progress")
data class BookProgress(
    @PrimaryKey val bookId: String,
    val fileIndex: Int,
    val trackRatingKey: String,
    val positionMs: Long,
    val updatedAt: Long,
    val lastPlayedAt: Long,
    val speed: Float = 1f,
    val finished: Boolean = false,
)

/** Pozice knihy odvozená ze stavu stop na Plex serveru. */
data class ServerPosition(
    val fileIndex: Int,
    val trackRatingKey: String,
    val positionMs: Long,
    val updatedAt: Long,
    val finished: Boolean,
)
