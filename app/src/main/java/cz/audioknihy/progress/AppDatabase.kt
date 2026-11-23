package cz.audioknihy.progress

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    suspend fun get(bookId: String): BookProgress?

    @Upsert
    suspend fun upsert(p: BookProgress)

    @Query("SELECT * FROM progress WHERE finished = 0 AND lastPlayedAt > 0 ORDER BY lastPlayedAt DESC")
    fun inProgress(): Flow<List<BookProgress>>

    @Query("SELECT * FROM progress")
    fun all(): Flow<List<BookProgress>>
}

/** Kniha uložená v cache jako JSON (stopy se mění zřídka, čte se vždy celá). */
@Entity(tableName = "books")
data class CachedBook(@PrimaryKey val id: String, val json: String)

@Entity(tableName = "chapters")
data class CachedChapters(@PrimaryKey val bookId: String, val json: String)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM books")
    fun all(): Flow<List<CachedBook>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): CachedBook?

    @Query("DELETE FROM books")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<CachedBook>)

    @Transaction
    suspend fun replaceAll(books: List<CachedBook>) {
        clear()
        insertAll(books)
    }

    @Query("SELECT * FROM chapters WHERE bookId = :bookId")
    suspend fun chapters(bookId: String): CachedChapters?

    @Upsert
    suspend fun putChapters(c: CachedChapters)

    @Query("SELECT * FROM covers WHERE `key` = :key")
    suspend fun cover(key: String): cz.audioknihy.covers.CachedCover?

    @Upsert
    suspend fun putCover(c: cz.audioknihy.covers.CachedCover)
}

@Dao
interface ReportDao {
    @Insert
    suspend fun insert(r: PendingReport): Long

    @Query("SELECT * FROM pending_reports ORDER BY id")
    suspend fun all(): List<PendingReport>

    @Query("DELETE FROM pending_reports WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_reports WHERE kind = 'timeline' AND ratingKey = :ratingKey")
    suspend fun deleteTimeline(ratingKey: String)

    @Query("DELETE FROM pending_reports WHERE kind = 'timeline'")
    suspend fun deleteAllTimelines()
}

@Database(
    entities = [BookProgress::class, CachedBook::class, CachedChapters::class, PendingReport::class, cz.audioknihy.covers.CachedCover::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `covers` (`key` TEXT NOT NULL, `url` TEXT, `checkedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            }
        }
    }

    abstract fun progressDao(): ProgressDao
    abstract fun libraryDao(): LibraryDao
    abstract fun reportDao(): ReportDao
}
