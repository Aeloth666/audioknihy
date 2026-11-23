package cz.audioknihy.di

import android.content.Context
import android.os.Build
import androidx.room.Room
import cz.audioknihy.chapters.ChapterRepository
import cz.audioknihy.covers.CachedCover
import cz.audioknihy.covers.CoverCache
import cz.audioknihy.covers.CoverSearch
import cz.audioknihy.covers.Covers
import cz.audioknihy.library.LibraryRepository
import cz.audioknihy.playback.BookSession
import cz.audioknihy.playback.ExoPlayerFacade
import cz.audioknihy.playback.PlaybackPrefs
import cz.audioknihy.plex.PlexAuth
import cz.audioknihy.plex.PlexHeaders
import cz.audioknihy.plex.ServerConnector
import cz.audioknihy.plex.ServerSession
import cz.audioknihy.progress.AppDatabase
import cz.audioknihy.progress.ProgressDao
import cz.audioknihy.progress.ProgressResolver
import cz.audioknihy.progress.ReportQueue
import cz.audioknihy.progress.ReportWorker
import cz.audioknihy.settings.Settings
import cz.audioknihy.sleep.SleepTimer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Scope celé aplikace (hlášení do Plexu, nouzové ukládání). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides @Singleton
    fun settings(@ApplicationContext ctx: Context) = Settings(ctx)

    @Provides @Singleton
    fun http(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun headers(settings: Settings): PlexHeaders =
        PlexHeaders(settings.clientId(), "${Build.MANUFACTURER} ${Build.MODEL}")

    @Provides @Singleton
    fun auth(http: OkHttpClient, headers: PlexHeaders) = PlexAuth(http, headers)

    @Provides @Singleton
    fun connector(http: OkHttpClient, headers: PlexHeaders) = ServerConnector(http, headers)

    @Provides @Singleton
    fun serverSession(settings: Settings, http: OkHttpClient, headers: PlexHeaders, connector: ServerConnector) =
        ServerSession(settings, http, headers, connector)

    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "audioknihy.db").addMigrations(AppDatabase.MIGRATION_1_2).build()

    @Provides
    fun progressDao(db: AppDatabase): ProgressDao = db.progressDao()

    @Provides @Singleton
    fun reportQueue(@ApplicationContext ctx: Context, db: AppDatabase, server: ServerSession) =
        ReportQueue(db.reportDao(), server, schedule = { ReportWorker.schedule(ctx) }, clock = System::currentTimeMillis)

    @Provides @Singleton
    fun library(db: AppDatabase, server: ServerSession) = LibraryRepository(db.libraryDao(), db.progressDao(), server)

    @Provides @Singleton
    fun chapters(db: AppDatabase, server: ServerSession) = ChapterRepository(db.libraryDao(), server)

    @Provides @Singleton
    fun covers(db: AppDatabase, http: OkHttpClient, server: ServerSession): Covers {
        val dao = db.libraryDao()
        val cache = object : CoverCache {
            override suspend fun get(key: String) = dao.cover(key)
            override suspend fun put(c: CachedCover) = dao.putCover(c)
        }
        return Covers(cache, CoverSearch(http), plexUrl = { thumb, size -> server.imageUrl(thumb, size) }, clock = System::currentTimeMillis)
    }

    @Provides @Singleton
    fun sleepTimer() = SleepTimer(System::currentTimeMillis)

    @Provides @Singleton
    fun player(@ApplicationContext ctx: Context) = ExoPlayerFacade(ctx)

    @Provides @Singleton
    fun prefs(settings: Settings): PlaybackPrefs = object : PlaybackPrefs {
        override suspend fun skipBackMs() = settings.skipBackMs.first()
        override suspend fun skipForwardMs() = settings.skipForwardMs.first()
        override suspend fun resumeRewindMs() = settings.resumeRewindMs.first()
        override suspend fun syncToServer() = settings.syncPositionToServer.first()
    }

    @Provides @Singleton
    fun session(
        player: ExoPlayerFacade,
        progress: ProgressDao,
        chapters: ChapterRepository,
        reports: ReportQueue,
        prefs: PlaybackPrefs,
        server: ServerSession,
        covers: Covers,
        timer: SleepTimer,
        @AppScope scope: CoroutineScope,
    ): BookSession = BookSession(
        player = player,
        progress = progress,
        chapterSource = chapters,
        reporter = reports,
        prefs = prefs,
        uris = { track -> server.client()?.streamUrl(track.partKey) ?: "" },
        clock = System::currentTimeMillis,
        sleepTimer = timer,
        artwork = { book -> covers.immediate(book, 600) },
        fire = { block -> scope.launch { block() } },
        serverCheck = { book ->
            val fresh = server.get()?.source?.tracksMetadata(book.files.map { it.ratingKey })?.associateBy { it.ratingKey }
            fresh?.let { m -> ProgressResolver.serverPosition(book.copy(files = book.files.map { m[it.ratingKey] ?: it })) }
        },
    )
}
