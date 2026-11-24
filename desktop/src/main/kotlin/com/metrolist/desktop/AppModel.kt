package com.metrolist.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.AlbumPage
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.BrowseResult
import com.metrolist.innertube.pages.ChartsPage
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.innertube.pages.HistoryPage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.pages.MoodAndGenres
import com.metrolist.innertube.pages.PlaylistPage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Songs of a remote playlist together with the token of its next page. */
data class PlaylistState(val page: PlaylistPage, val continuation: String?)

class AppModel {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, failure ->
        // A failed write or request must not take the window down with it.
        javax.swing.SwingUtilities.invokeLater { status = friendlyError(failure) }
    })
    val store = Store()
    private val resolver = StreamResolver(appDir())
    internal val updates = Updater(scope, store, notify = TaskbarMedia::announce)
    private val discord = DiscordSession(WindowsDiscordTransport())
    var blocks by mutableStateOf(store.blocks)
    var discordEnabled by mutableStateOf(store.settings.discord)
    var discordAppId by mutableStateOf(store.settings.discordAppId)
    var privateListen by mutableStateOf(store.settings.privateListen)
    val player = PlaybackController(
        resolver = resolver,
        scope = scope,
        quality = { store.settings.quality },
        onListening = { session ->
            if (closing) store.addSession(session)
            else listeningWrite = scope.launch(Dispatchers.IO) { store.addSession(session) }
        },
        autoplay = { store.settings.autoplay },
        autoSkipped = { track -> matchesBlock(track, store.blocks) },
        report = { event -> events = (listOf(event) + events).take(80) },
        onPlayed = { track ->
            scope.launch(Dispatchers.IO) {
                store.addHistory(track)
                val snapshot = store.history
                withContext(Dispatchers.Main) { history = snapshot }
            }
        },
    )

    @Volatile private var closing = false
    private var listeningWrite: Job? = null
    var events by mutableStateOf<List<PlaybackEvent>>(emptyList())
        private set
    var stack by mutableStateOf(listOf<Dest>(Dest.Home))
    /** Direction of the last navigation; page transitions follow it. */
    var forward by mutableStateOf(true)
        private set
    /** Loaded pages by destination, so a slow answer can never appear under another page's title. */
    val pages = mutableStateMapOf<Dest, Any>()
    private val pageOrder = ArrayDeque<Dest>()
    /** Increases each time the search field should take the keyboard. */
    var focusSearch by mutableIntStateOf(0)
    var showPalette by mutableStateOf(false)
    var showNowPlaying by mutableStateOf(false)
    var motion by mutableStateOf(store.settings.motion)
        private set
    var radioLoading by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var status by mutableStateOf<String?>(store.warning)
    var home by mutableStateOf<HomePage?>(null)
    var homeContinuation by mutableStateOf<String?>(null)
    var homePaging by mutableStateOf(false)
    var query by mutableStateOf("")
    var suggestions by mutableStateOf<List<String>>(emptyList())
    var searchFilter by mutableStateOf(YouTube.SearchFilter.FILTER_SONG)
    var searchItems by mutableStateOf<List<YTItem>>(emptyList())
    var searchContinuation by mutableStateOf<String?>(null)
    var libraryTab by mutableStateOf(LibraryTab.Playlists)
    var libraryItems by mutableStateOf<List<YTItem>>(emptyList())
    var libraryContinuation by mutableStateOf<String?>(null)
    var history by mutableStateOf(store.history)
    var liked by mutableStateOf(store.library.liked)
    var localPlaylists by mutableStateOf(store.library.playlists)
    var remoteHistory by mutableStateOf<List<HistoryPage.HistorySection>>(emptyList())
    var explore by mutableStateOf<ExplorePage?>(null)
    var charts by mutableStateOf<ChartsPage?>(null)
    var moods by mutableStateOf<List<MoodAndGenres>>(emptyList())
    var releases by mutableStateOf<List<AlbumItem>>(emptyList())
    var categoryId by mutableStateOf<String?>(null)
    var categoryTitle by mutableStateOf("")
    var categorySongs by mutableStateOf<List<SongItem>>(emptyList())
    var categoryAlbums by mutableStateOf<List<AlbumItem>>(emptyList())
    var categoryLists by mutableStateOf<List<PlaylistItem>>(emptyList())
    var categoryLoading by mutableStateOf(false)
    var accountName by mutableStateOf<String?>(null)
    var cookieDraft by mutableStateOf(store.settings.cookie)
    internal var look by mutableStateOf(Look.from(store.settings))
    var quality by mutableStateOf(store.settings.quality)
    var widgetOn by mutableStateOf(store.settings.widget)
    var widgetX by mutableStateOf(store.settings.widgetX)
    var widgetY by mutableStateOf(store.settings.widgetY)
    var toastOn by mutableStateOf(store.settings.toast)
    var toastOpacity by mutableStateOf(store.settings.toastOpacity)
    var toastScale by mutableStateOf(store.settings.toastScale)
    var toastSeconds by mutableStateOf(store.settings.toastSeconds.coerceIn(1, 8))
    var toastColor by mutableStateOf(store.settings.toastColor)
    var toastX by mutableStateOf(store.settings.toastX)
    var toastY by mutableStateOf(store.settings.toastY)
    var toastShown by mutableStateOf(false)
    var toastHold by mutableStateOf(false)
    var barOn by mutableStateOf(store.settings.bar)
    var barX by mutableStateOf(store.settings.barX)
    var barScale by mutableStateOf(store.settings.barScale)
    var caption by mutableStateOf(store.settings.caption)
    var captionText by mutableStateOf(store.settings.captionText)
    var captionStart by mutableStateOf(store.settings.captionStart)
    var captionEnd by mutableStateOf(store.settings.captionEnd)
    var captionCorner by mutableStateOf(store.settings.captionCorner)
    var playlistsForAdd by mutableStateOf<List<PlaylistItem>>(emptyList())
    var showAdd by mutableStateOf(false)
    /** The songs the add dialog was opened for; the playing track may change while it is open. */
    var addTargets by mutableStateOf<List<Track>>(emptyList())
        private set
    var showCreate by mutableStateOf(false)
    var newPlaylistTitle by mutableStateOf("")
    /** The "Sana özel" page opens the same Ctrl+K palette. */
    var quickOpen: Boolean
        get() = showPalette
        set(value) { showPalette = value }
    private var loadGeneration by mutableIntStateOf(0)
    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private var pagingJob: Job? = null
    private var chromeJob: Job? = null
    private var homeAt = 0L
    private var exploreAt = 0L
    val libraryTools = LibraryTools(this)

    val current: Dest get() = stack.last()

    init {
        applyCookie(store.settings.cookie)
        player.restore(store.playback)
        player.configureAudio(store.settings.validated().audio)
        updates.start()
        startLiveFeed()
        // The decoder and the extractor are slow to load the first time; do it before the first song needs them.
        scope.launch(Dispatchers.IO) {
            runCatching { org.bytedeco.javacv.FFmpegFrameGrabber.tryLoad() }
            runCatching { resolver.prepare() }
        }
        // Runs in the model so playback keeps advancing while the window is hidden in the tray.
        scope.launch {
            while (true) {
                delay(300)
                player.poll()
            }
        }
        scope.launch {
            var ticks = 0
            while (true) {
                delay(5_000)
                // Snapshot on the UI thread, write on a background one; an unchanged state is not written again.
                val memory = player.memory()
                if (++ticks % 6 == 0) player.flushListening()
                runCatching { withContext(Dispatchers.IO) { store.savePlayback(memory) } }
                    .onFailure { status = "Oynatma kaydı yazılamadı: ${friendlyError(it)}" }
            }
        }
    }

    fun reloadPreferences() {
        val settings = store.settings
        discordEnabled = settings.discord; discordAppId = settings.discordAppId; privateListen = settings.privateListen
        chromeJob?.cancel(); chromeJob = null
        motion = settings.motion
        look = Look.from(settings)
        quality = settings.quality
        widgetOn = settings.widget; widgetX = settings.widgetX; widgetY = settings.widgetY
        toastOn = settings.toast; toastOpacity = settings.toastOpacity; toastScale = settings.toastScale
        toastSeconds = settings.toastSeconds.coerceIn(1, 8); toastColor = settings.toastColor
        toastX = settings.toastX; toastY = settings.toastY
        barOn = settings.bar; barX = settings.barX; barScale = settings.barScale
        caption = settings.caption; captionText = settings.captionText
        captionStart = settings.captionStart; captionEnd = settings.captionEnd; captionCorner = settings.captionCorner
        player.configureAudio(settings.audio)
        updates.reloadPreferences()
    }

    fun installUpdate() {
        updates.install {
            rememberPlayback()
            player.stop()
            kotlin.system.exitProcess(0)
        }
    }

    fun rememberPlayback() {
        closing = true
        player.poll()
        player.flushListening()
        runCatching { flushChrome() }
        runCatching { kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeoutOrNull(3_000) { listeningWrite?.join() } } }
        runCatching { store.savePlayback(player.memory()) }
    }

    fun openRail(dest: Dest) {
        val again = stack.size == 1 && current == dest
        forward = true
        showNowPlaying = false
        stack = listOf(dest)
        refresh(force = again)
    }

    fun open(dest: Dest) {
        showNowPlaying = false
        if (current == dest) { refresh(); return }
        forward = true
        stack = stack + dest
        refresh()
    }

    fun back() {
        if (showNowPlaying) { showNowPlaying = false; return }
        if (stack.size <= 1) return
        forward = false
        stack = stack.dropLast(1)
        refresh()
    }

    fun refresh(force: Boolean = false) {
        val dest = current
        val generation = ++loadGeneration
        loading = true
        error = null
        scope.launch {
            val failure = runCatching { load(dest, force) }.exceptionOrNull()
            if (generation != loadGeneration) return@launch
            loading = false
            error = failure?.let(::friendlyError)
        }
    }

    fun submitSearch() {
        val text = query.trim()
        showNowPlaying = false
        if (current !is Dest.Search) { forward = true; stack = stack + Dest.Search }
        if (text.isNotBlank()) search(text)
    }

    fun search(text: String = query) {
        query = text
        suggestJob?.cancel()
        suggestions = emptyList()
        searchJob?.cancel(); pagingJob?.cancel()
        // Takes over the loading indicator from any page that is still arriving.
        val generation = ++loadGeneration
        loading = true
        val filter = searchFilter
        searchJob = scope.launch {
            val result = withContext(Dispatchers.IO) { YouTube.search(text, filter) }
            if (generation == loadGeneration) loading = false
            result.onSuccess {
                searchItems = it.items
                searchContinuation = it.continuation
                error = null
            }.onFailure { error = friendlyError(it) }
        }
    }

    fun suggest(text: String) {
        query = text
        suggestJob?.cancel()
        if (text.isBlank()) {
            suggestions = emptyList()
            return
        }
        suggestJob = scope.launch {
            delay(300)
            val result = withContext(Dispatchers.IO) { YouTube.searchSuggestions(text).getOrNull() }
            if (query == text) suggestions = result?.queries.orEmpty().take(6)
        }
    }

    fun loadMoreSearch() {
        val token = searchContinuation ?: return
        if (pagingJob?.isActive == true) return
        pagingJob = scope.launch {
            val result = withContext(Dispatchers.IO) { YouTube.searchContinuation(token) }
            result.onSuccess {
                searchItems = (searchItems + it.items).distinctBy { item -> item.id }
                searchContinuation = it.continuation
            }.onFailure { searchContinuation = null; status = friendlyError(it) }
        }
    }

    private var homeLoadedAt = 0L

    fun loadMoreHome() {
        val now = System.currentTimeMillis()
        if (homePaging || home == null || now - homeLoadedAt < 700) return
        homeLoadedAt = now
        homePaging = true
        scope.launch {
            runCatching { appendHome() }
            homePaging = false
        }
    }

    fun loadMorePlaylist(dest: Dest.Playlist) {
        val token = (pages[dest] as? PlaylistState)?.continuation ?: return
        if (pagingJob?.isActive == true) return
        pagingJob = scope.launch {
            val result = withContext(Dispatchers.IO) { YouTube.playlistContinuation(token) }
            val shown = pages[dest] as? PlaylistState ?: return@launch
            result.onSuccess { next ->
                pages[dest] = PlaylistState(shown.page.copy(songs = shown.page.songs + next.songs), next.continuation?.takeIf { it != token })
            }.onFailure { pages[dest] = shown.copy(continuation = null); status = friendlyError(it) }
        }
    }

    fun selectLibrary(tab: LibraryTab) {
        if (tab == libraryTab) return
        libraryTab = tab
        libraryItems = emptyList(); libraryContinuation = null
        refresh()
    }

    fun loadMoreLibrary() {
        val token = libraryContinuation ?: return
        if (pagingJob?.isActive == true) return
        val tab = libraryTab
        pagingJob = scope.launch {
            val result = withContext(Dispatchers.IO) { YouTube.libraryContinuation(token) }
            if (tab != libraryTab) return@launch
            result.onSuccess { page ->
                libraryItems = (libraryItems + page.items).distinctBy { it.id }
                libraryContinuation = page.continuation
            }.onFailure { libraryContinuation = null; status = friendlyError(it) }
        }
    }

    fun openItem(item: YTItem) {
        when (item) {
            is SongItem -> player.playAll(listOf(item.toTrack()), 0)
            is AlbumItem -> open(Dest.Album(item.browseId))
            is com.metrolist.innertube.models.ArtistItem -> open(Dest.Artist(item.id))
            is PlaylistItem -> open(Dest.Playlist(item.id))
            is com.metrolist.innertube.models.PodcastItem -> open(Dest.Playlist(item.id))
            is com.metrolist.innertube.models.EpisodeItem -> player.playAll(listOf(item.toTrack()), 0)
        }
    }

    fun playSongs(songs: List<SongItem>, start: Int) {
        player.playAll(songs.map { it.toTrack() }, start)
    }

    fun openGenre(id: String, title: String, query: String) {
        if (categoryId == id) {
            categoryId = null
            return
        }
        categoryId = id
        categoryTitle = title
        categoryLoading = true
        categorySongs = emptyList()
        categoryAlbums = emptyList()
        categoryLists = emptyList()
        scope.launch {
            val songs = async(Dispatchers.IO) {
                YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items?.filterIsInstance<SongItem>().orEmpty()
            }
            val albums = async(Dispatchers.IO) {
                YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()?.items?.filterIsInstance<AlbumItem>().orEmpty()
            }
            val lists = async(Dispatchers.IO) {
                YouTube.search(query, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST).getOrNull()?.items?.filterIsInstance<PlaylistItem>().orEmpty()
            }
            val songItems = songs.await()
            val albumItems = albums.await()
            val listItems = lists.await()
            if (categoryId != id) return@launch
            categorySongs = songItems.take(10)
            categoryAlbums = albumItems.take(12)
            categoryLists = listItems.take(12)
            categoryLoading = false
        }
    }

    fun likeCurrent() {
        val track = player.current ?: return
        toggleLike(track)
    }

    fun isLiked(id: String): Boolean = liked.any { it.id == id }

    fun toggleLike(track: Track) {
        val added = store.toggleLike(track.toSaved())
        liked = store.library.liked
        status = if (added) "Beğenildi" else "Beğeni kaldırıldı"
        if (track.localPath == null) libraryTools.queueSync(SyncAction("like", value = track.id, enabled = added))
    }

    fun startRadio(track: Track) {
        if (track.localPath != null) { status = "Yerel dosyalardan radyo başlatılamaz"; return }
        if (radioLoading) return
        radioLoading = true
        status = "Radyo hazırlanıyor…"
        scope.launch {
            try {
                status = if (player.startRadio(track)) "${track.title} radyosu" else "Benzer şarkı bulunamadı"
            } finally { radioLoading = false }
        }
    }

    /** Same blocks as the player menu, for any song in a list. They only keep songs out of the automatic queue. */
    fun block(track: Track) {
        store.setSongBlocked(track.id, true)
        blocks = store.blocks
        player.applyBlocks()
        status = "Bu şarkı artık radyoda çalmaz"
    }

    fun blockArtist(track: Track) {
        val names = artistNames(track.artists)
        if (names.isEmpty()) return
        store.setArtistsBlocked(names, true)
        blocks = store.blocks
        player.applyBlocks()
        status = "Bu sanatçı artık radyoda çalmaz"
    }

    fun unblock(id: String) { store.setSongBlocked(id, false); blocks = store.blocks }

    fun unblockArtist(name: String) { store.setArtistsBlocked(listOf(name), false); blocks = store.blocks }

    fun copyLink(track: Track) {
        if (track.localPath != null) return
        val link = "https://music.youtube.com/watch?v=${track.id}"
        runCatching {
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(link), null)
        }.onSuccess { status = "Bağlantı kopyalandı" }.onFailure { status = "Pano şu an kullanılamıyor" }
    }

    /** Plain-text report without addresses, account data or folder names. */
    fun diagnosticsReport(): String = buildString {
        appendLine("Metrolist ${AppVersion.NAME} · ${System.getProperty("os.name")} · Java ${System.getProperty("java.version")}")
        appendLine("Kalite: $quality · crossfade: ${player.audio.crossfadeSeconds} sn · oturum: ${if (store.settings.cookie.isBlank()) "yok" else "var"}")
        val clock = java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm:ss").withZone(java.time.ZoneId.systemDefault())
        events.forEach { event ->
            append(clock.format(java.time.Instant.ofEpochMilli(event.at))).append(if (event.failed) "  HATA  " else "  ")
            append(event.track).append(" · ").append(event.stage).append(": ").append(event.detail)
            if (event.tookMs > 0) append(" (").append(event.tookMs).append(" ms)")
            appendLine()
        }
    }

    fun copyDiagnostics() {
        runCatching {
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(diagnosticsReport()), null)
        }.onSuccess { status = "Tanılama raporu kopyalandı" }.onFailure { status = "Pano şu an kullanılamıyor" }
    }

    fun clearDiagnostics() { events = emptyList() }

    fun prepareAdd() { player.current?.let { prepareAdd(listOf(it)) } }

    fun prepareAdd(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        addTargets = tracks
        playlistsForAdd = emptyList()
        showAdd = true
        if (store.settings.cookie.isBlank() || tracks.any { it.localPath != null }) return
        scope.launch {
            val page = withContext(Dispatchers.IO) { YouTube.library("FEmusic_liked_playlists").getOrNull() }
            val linked = store.library.playlists.flatMap { it.remoteBindings.values }.toSet()
            if (addTargets === tracks) playlistsForAdd = page?.items?.filterIsInstance<PlaylistItem>().orEmpty().filterNot { it.id in linked }
        }
    }

    fun addTargetsTo(playlistId: String) {
        val tracks = addTargets
        showAdd = false
        if (tracks.isEmpty()) return
        if (playlistId.startsWith("local-")) {
            libraryTools.addTracks(playlistId, tracks.map { it.toSaved() })
            return
        }
        val remote = tracks.filter { it.localPath == null }
        scope.launch {
            val failure = withContext(Dispatchers.IO) {
                remote.firstNotNullOfOrNull { YouTube.addToPlaylist(playlistId, it.id).exceptionOrNull() }
            }
            status = if (failure == null) "YouTube listesine eklendi" else friendlyError(failure)
        }
    }

    fun createPlaylist() {
        val title = newPlaylistTitle.trim()
        if (title.isBlank()) {
            status = "Liste adı yaz"
            return
        }
        val tracks = if (addAfterCreate) addTargets.map { it.toSaved() } else emptyList()
        addAfterCreate = false
        libraryTools.createPlaylist(title, tracks)
        localPlaylists = store.library.playlists
        newPlaylistTitle = ""
        showCreate = false
        status = if (tracks.isEmpty()) "Liste oluşturuldu" else "Liste oluşturuldu, ${tracks.size} şarkı eklendi"
    }

    private var addAfterCreate = false

    fun createForTargets() {
        addAfterCreate = true
        showAdd = false
        showCreate = true
    }

    fun cancelCreate() { addAfterCreate = false; showCreate = false }

    fun saveAsPlaylist(title: String, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        libraryTools.createPlaylist(title.ifBlank { "Yeni liste" }, tracks.map { it.toSaved() })
        status = "Kitaplığa kaydedildi: ${tracks.size} şarkı"
    }

    fun openLocal(id: String) {
        open(Dest.LocalList(id))
    }

    private fun startLiveFeed() {
        scope.launch {
            while (true) {
                delay(45_000)
                when (current) {
                    Dest.Explore -> runCatching { loadExplore() }
                    else -> Unit
                }
            }
        }
    }

    private val homeMix = listOf(
        "türkçe pop",
        "arabesk",
        "türkçe rock",
        "lofi",
        "türkçe rap",
        "akustik",
        "caz",
        "elektronik",
        "halk müziği",
        "indie",
        "90lar türkçe",
        "oyun müziği",
    )
    private var homeMixCursor = 0
    private var homeEpoch = 0
    private val seenHome = mutableSetOf<String>()

    private suspend fun appendHome() {
        val token = homeContinuation
        // Results that arrive after the page was reloaded belong to the old page.
        val epoch = homeEpoch
        if (!token.isNullOrBlank()) {
            val page = withContext(Dispatchers.IO) { YouTube.home(continuation = token).getOrNull() }
            if (epoch != homeEpoch) return
            if (page != null && page.sections.isNotEmpty()) {
                addHomeSections(page.sections)
                homeContinuation = page.continuation
                return
            }
            homeContinuation = null
        }
        if (homeMixCursor >= homeMix.size * 3) return
        val query = homeMix[homeMixCursor % homeMix.size]
        val filter = when (homeMixCursor / homeMix.size) {
            0 -> YouTube.SearchFilter.FILTER_SONG
            1 -> YouTube.SearchFilter.FILTER_ALBUM
            else -> YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
        }
        homeMixCursor++
        val found = withContext(Dispatchers.IO) { YouTube.search(query, filter).getOrNull() }?.items.orEmpty()
            .distinctBy { it.id }
            .take(14)
        if (found.isEmpty() || epoch != homeEpoch) return
        val title = when (filter) {
            YouTube.SearchFilter.FILTER_ALBUM -> "$query albümleri"
            YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST -> "$query listeleri"
            else -> query.replaceFirstChar { it.titlecase(java.util.Locale.forLanguageTag("tr")) }
        }
        addHomeSections(listOf(HomePage.Section(title, null, null, null, found)))
    }

    private fun addHomeSections(incoming: List<HomePage.Section>) {
        val current = home ?: return
        val fresh = incoming.filter { section -> seenHome.add(sectionKey(section)) }
        if (fresh.isEmpty()) return
        home = current.copy(sections = current.sections + fresh)
    }

    private fun sectionKey(section: HomePage.Section) = section.title + section.items.take(4).joinToString { it.id }

    /** Writes a settings change; a full disk or locked file becomes a message instead of a crash. */
    private fun persist(change: (Settings) -> Settings): Boolean =
        runCatching { store.saveSettings(change(store.settings)) }
            .onFailure { status = "Ayar kaydedilemedi: ${friendlyError(it)}" }.isSuccess

    fun saveCookie() {
        val cookie = cleanCookie(cookieDraft)
        if (cookie.isNotBlank() && "SAPISID=" !in cookie) {
            status = "Bu çerezde SAPISID yok. music.youtube.com isteğinin Cookie başlığının tamamını yapıştır."
            return
        }
        if (!persist { it.copy(cookie = cookie) }) return
        cookieDraft = cookie
        applyCookie(cookie)
        status = if (cookie.isBlank()) "Çıkış yapıldı" else "Çerez kaydedildi"
        accountName = null
        // Everything on screen may belong to the previous account.
        pages.clear(); pageOrder.clear()
        home = null; homeAt = 0; exploreAt = 0
        libraryItems = emptyList(); libraryContinuation = null; remoteHistory = emptyList(); playlistsForAdd = emptyList()
        refresh(force = true)
        libraryTools.retrySync()
    }

    fun saveQuality(quality: String) {
        this.quality = quality
        persist { it.copy(quality = quality) }
    }

    fun changeMotion(on: Boolean) { motion = on; persist { it.copy(motion = on) } }


    fun saveWindow(x: Int, y: Int, width: Int, height: Int, maximized: Boolean) {
        val old = store.settings
        val next = if (maximized) old.copy(windowMaximized = true)
            else old.copy(windowX = x, windowY = y, windowWidth = width, windowHeight = height, windowMaximized = false)
        if (next != old) persist { next }
    }

    /** True when a window restored at this place would still have a reachable title bar. */
    internal fun placeVisible(x: Int, y: Int): Boolean = x != NO_PLACE && y != NO_PLACE && onScreens(x + 120, y + 10)

    fun setWidget(on: Boolean) {
        widgetOn = on
        saveChrome()
    }

    fun placeWidget(x: Int, y: Int) {
        if (!onScreens(x, y)) return
        widgetX = x
        widgetY = y
        saveChrome()
    }

    fun placeToast(x: Int, y: Int) {
        if (!onScreens(x, y)) return
        toastX = x
        toastY = y
        saveChrome()
    }

    fun setToast(on: Boolean) {
        toastOn = on
        if (!on) toastShown = false
        saveChrome()
    }

    fun saveToastOpacity(value: Float) {
        toastOpacity = value.coerceIn(0.35f, 1f)
        saveChrome()
    }

    fun saveToastScale(value: Float) {
        toastScale = value
        saveChrome()
    }

    fun saveToastSeconds(seconds: Int) {
        toastSeconds = seconds.coerceIn(1, 8)
        saveChrome()
    }

    fun saveToastColor(id: String) {
        toastColor = id
        saveChrome()
    }

    fun setBar(on: Boolean) {
        barOn = on
        saveChrome()
    }

    fun placeBar(along: Int) {
        if (along < 0) return
        barX = along
        saveChrome()
    }

    fun saveBarScale(value: Float) {
        barScale = value
        saveChrome()
    }

    fun saveCaption(id: String) {
        caption = id
        saveChrome()
    }

    fun saveCaptionText(id: String) {
        captionText = id
        saveChrome()
    }

    fun saveCaptionCorner(id: String) {
        captionCorner = id
        saveChrome()
    }

    fun saveCaptionStop(start: Boolean, hex: String) {
        if (start) captionStart = hex else captionEnd = hex
        caption = "custom"
        saveChrome()
    }

    /** Sliders and drags call this continuously; only the final value reaches the disk. */
    private fun saveChrome() {
        chromeJob?.cancel()
        chromeJob = scope.launch {
            delay(350)
            chromeJob = null
            writeChrome()
        }
    }

    private fun flushChrome() {
        if (chromeJob == null) return
        chromeJob?.cancel(); chromeJob = null
        writeChrome()
    }

    private fun writeChrome() {
        persist {
            it.copy(
                widget = widgetOn,
                widgetX = widgetX,
                widgetY = widgetY,
                toast = toastOn,
                toastOpacity = toastOpacity,
                toastScale = toastScale,
                toastSeconds = toastSeconds,
                toastColor = toastColor,
                toastX = toastX,
                toastY = toastY,
                bar = barOn,
                barX = barX,
                barScale = barScale,
                caption = caption,
                captionText = captionText,
                captionStart = captionStart,
                captionEnd = captionEnd,
                captionCorner = captionCorner,
            )
        }
    }

    fun saveDiscordEnabled(enabled: Boolean) {
        discordEnabled = enabled
        store.saveSettings(store.settings.copy(discord = enabled))
        syncDiscord()
    }

    fun saveDiscordAppId(value: String) {
        discordAppId = value
        store.saveSettings(store.settings.copy(discordAppId = value.trim()))
        syncDiscord()
    }

    fun savePrivateListen(enabled: Boolean) {
        privateListen = enabled
        store.saveSettings(store.settings.copy(privateListen = enabled))
        syncDiscord()
    }

    fun blockCurrentSong() {
        val track = player.current ?: return
        val blocked = !blocks.songs.contains(track.id)
        store.setSongBlocked(track.id, blocked)
        blocks = store.blocks
        if (blocked) player.applyBlocks()
        status = if (blocked) "Bu şarkı artık radyoda çalmaz" else "Şarkı engeli kalktı"
        syncDiscord()
    }

    fun blockCurrentArtists() {
        val track = player.current ?: return
        val names = artistNames(track.artists)
        if (names.isEmpty()) {
            status = "Sanatçı adı yok"
            return
        }
        val blocked = names.any { artistKey(it) !in blocks.artists.map(::artistKey).toSet() }
        store.setArtistsBlocked(names, blocked)
        blocks = store.blocks
        if (blocked) player.applyBlocks()
        status = if (blocked) "Bu sanatçı artık radyoda çalmaz" else "Sanatçı engeli kalktı"
    }

    fun syncDiscord() {
        val settings = store.settings
        val track = player.current
        val visible = settings.discord && !settings.privateListen && player.isPlaying
        val positionMs = player.positionMs
        val durationMs = player.durationMs.takeIf { it > 1_000 } ?: track?.durationMs ?: 0L
        val appId = settings.discordAppId
        scope.launch(Dispatchers.IO) {
            discord.sync(appId, visible, track, positionMs, durationMs)
        }
    }

    internal fun saveLook(
        accent: String = look.accentId,
        surface: String = look.surfaceId,
        corners: String = look.cornerId,
        cards: String = look.cardId,
    ) {
        look = Look(accent, surface, corners, cards)
        persist { it.copy(accent = accent, surface = surface, corners = corners, cards = cards) }
    }

    private fun applyCookie(cookie: String) {
        YouTube.cookie = cookie.ifBlank { null }
        YouTube.useLoginForBrowse = cookie.isNotBlank()
    }

    private fun fresh(at: Long, minutes: Int) = System.currentTimeMillis() - at < minutes * 60_000L

    private suspend fun <T : Any> loadPage(dest: Dest, force: Boolean, fetch: suspend () -> T) {
        if (!force && pages[dest] != null) return
        val page = withContext(Dispatchers.IO) { fetch() }
        pages[dest] = page
        pageOrder.remove(dest); pageOrder.addLast(dest)
        while (pageOrder.size > 24) pageOrder.removeFirst().let { if (it !in stack) pages.remove(it) }
    }

    private suspend fun load(dest: Dest, force: Boolean = false) {
        when (dest) {
            Dest.Home -> if (force || home == null || !fresh(homeAt, 20)) {
                withContext(Dispatchers.IO) { YouTube.home() }.getOrThrow().also {
                    homeEpoch++
                    homeAt = System.currentTimeMillis()
                    homeMixCursor = 0
                    seenHome.clear()
                    it.sections.forEach { section -> seenHome.add(sectionKey(section)) }
                    home = it
                    homeContinuation = it.continuation
                }
            }
            Dest.Search -> Unit
            Dest.Library -> loadLibrary()
            Dest.History -> {
                history = store.history
                if (store.settings.cookie.isNotBlank()) {
                    remoteHistory = withContext(Dispatchers.IO) { YouTube.musicHistory().getOrNull()?.sections.orEmpty() }
                }
            }
            Dest.Explore -> if (force || !fresh(exploreAt, 10)) loadExplore()
            Dest.Settings -> {
                if (store.settings.cookie.isNotBlank()) {
                    accountName = withContext(Dispatchers.IO) { YouTube.accountInfo().getOrNull()?.name }
                }
            }
            Dest.Liked -> liked = store.library.liked
            Dest.Shortcuts, Dest.LocalMusic, Dest.Insights, Dest.Diagnostics, Dest.Blocked -> Unit
            is Dest.Smart -> Unit
            Dest.Personal -> Unit
            is Dest.LocalList -> Unit
            is Dest.Album -> loadPage(dest, force) { YouTube.album(dest.browseId).getOrThrow() }
            is Dest.Artist -> loadPage(dest, force) { YouTube.artist(dest.browseId).getOrThrow() }
            is Dest.Playlist -> loadPage(dest, force) {
                YouTube.playlist(dest.playlistId).getOrThrow().let { PlaylistState(it, it.songsContinuation) }
            }
            is Dest.Collection -> loadPage(dest, force) { YouTube.browse(dest.browseId, dest.params).getOrThrow() }
        }
    }

    private suspend fun loadLibrary() {
        if (store.settings.cookie.isBlank()) {
            libraryItems = emptyList()
            libraryContinuation = null
            return
        }
        val tab = libraryTab
        val page = withContext(Dispatchers.IO) { YouTube.library(tab.browseId).getOrThrow() }
        if (tab != libraryTab) return
        libraryItems = page.items.distinctBy { it.id }
        libraryContinuation = page.continuation
    }

    private fun onScreens(x: Int, y: Int): Boolean {
        val area = TaskbarMedia.desktopBounds()
        return x in (area.x - 80)..(area.x + area.width - 40) && y in (area.y - 40)..(area.y + area.height - 24)
    }

    private suspend fun loadExplore() {
        coroutineScope {
            val exploreCall = async(Dispatchers.IO) { YouTube.explore() }
            val chartsCall = async(Dispatchers.IO) { YouTube.getChartsPage() }
            val moodsCall = async(Dispatchers.IO) { YouTube.moodAndGenres() }
            val releasesCall = async(Dispatchers.IO) { YouTube.newReleaseAlbums() }
            val calls = listOf(exploreCall.await(), chartsCall.await(), moodsCall.await(), releasesCall.await())
            // A dropped connection keeps what is already on screen.
            exploreCall.await().onSuccess { explore = it }
            chartsCall.await().onSuccess { charts = it }
            moodsCall.await().onSuccess { moods = it }
            releasesCall.await().onSuccess { releases = it }
            if (calls.all { it.isFailure }) throw calls.first().exceptionOrNull()!!
            exploreAt = System.currentTimeMillis()
        }
    }
}

/** Accepts a whole copied header line, including wrapped text. */
internal fun cleanCookie(raw: String): String = raw.trim()
    .replace(Regex("^cookie\\s*:\\s*", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\s*[\\r\\n]+\\s*"), " ")
    .filter { it.code in 0x20..0x7E }.trim()

/** Turns transport exceptions into something a listener can act on. */
internal fun friendlyError(failure: Throwable): String {
    val chain = generateSequence(failure) { it.cause }.take(6).toList()
    return when {
        chain.any { it is java.net.UnknownHostException || it is java.net.ConnectException || it is java.net.NoRouteToHostException } ||
            chain.any { it.javaClass.simpleName.contains("UnresolvedAddress") } ->
            "İnternete ulaşılamadı. Bağlantını kontrol edip yeniden dene."
        chain.any { it is java.net.SocketTimeoutException || it.javaClass.simpleName.contains("Timeout") } ->
            "Sunucu zamanında yanıt vermedi. Yeniden dene."
        chain.any { it is javax.net.ssl.SSLException } -> "Güvenli bağlantı kurulamadı. Saat ve ağ ayarlarını kontrol et."
        chain.any { it is java.nio.file.AccessDeniedException } -> "Dosyaya erişim izni yok."
        else -> failure.message?.lineSequence()?.firstOrNull()?.replace(Regex("https?://\\S+"), "…")?.take(200)
            ?.takeIf { it.isNotBlank() } ?: failure.javaClass.simpleName
    }
}
