package com.metrolist.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppModel {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val store = Store()
    private val resolver = StreamResolver(appDir())
    internal val updates = Updater(scope, store, notify = TaskbarMedia::announce)
    val player = PlaybackController(
        resolver = resolver,
        scope = scope,
        quality = { store.settings.quality },
        onListening = { store.addSession(it) },
        autoplay = { store.settings.autoplay },
        onPlayed = { track ->
            scope.launch(Dispatchers.IO) {
                store.addHistory(track)
                val snapshot = store.history
                withContext(Dispatchers.Main) { history = snapshot }
            }
        },
    )

    var stack by mutableStateOf(listOf<Dest>(Dest.Home))
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
    var album by mutableStateOf<AlbumPage?>(null)
    var artist by mutableStateOf<ArtistPage?>(null)
    var playlist by mutableStateOf<PlaylistPage?>(null)
    var playlistContinuation by mutableStateOf<String?>(null)
    var collection by mutableStateOf<BrowseResult?>(null)
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
    var shortcuts by mutableStateOf(store.settings.shortcuts)
    internal var shortcutCapture by mutableStateOf<ShortcutSlot?>(null)
    var playlistsForAdd by mutableStateOf<List<PlaylistItem>>(emptyList())
    var showAdd by mutableStateOf(false)
    var showCreate by mutableStateOf(false)
    var newPlaylistTitle by mutableStateOf("")
    private var loadGeneration by mutableIntStateOf(0)
    private var suggestJob: Job? = null
    val libraryTools = LibraryTools(this)

    val current: Dest get() = stack.last()

    init {
        applyCookie(store.settings.cookie)
        player.restore(store.playback)
        player.configureAudio(store.settings.validated().audio)
        updates.start()
        startLiveFeed()
        scope.launch(Dispatchers.IO) {
            while (true) {
                delay(5_000)
                withContext(Dispatchers.Main) {
                    player.flushListening()
                    runCatching { store.savePlayback(player.memory()) }.onFailure { status = "Oynatma kaydı yazılamadı: ${it.message}" }
                }
            }
        }
    }

    fun reloadPreferences() {
        val settings = store.settings
        look = Look.from(settings)
        quality = settings.quality
        widgetOn = settings.widget; widgetX = settings.widgetX; widgetY = settings.widgetY
        toastOn = settings.toast; toastOpacity = settings.toastOpacity; toastScale = settings.toastScale
        toastSeconds = settings.toastSeconds.coerceIn(1, 8); toastColor = settings.toastColor
        toastX = settings.toastX; toastY = settings.toastY
        barOn = settings.bar; barX = settings.barX; barScale = settings.barScale
        caption = settings.caption; captionText = settings.captionText
        captionStart = settings.captionStart; captionEnd = settings.captionEnd; captionCorner = settings.captionCorner
        shortcuts = settings.shortcuts
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
        player.poll()
        player.flushListening()
        store.savePlayback(player.memory())
    }

    fun openRail(dest: Dest) {
        stack = listOf(dest)
        refresh()
    }

    fun open(dest: Dest) {
        stack = stack + dest
        refresh()
    }

    fun back() {
        if (stack.size > 1) stack = stack.dropLast(1)
        refresh()
    }

    fun refresh() {
        val dest = current
        val generation = ++loadGeneration
        loading = true
        error = null
        scope.launch {
            val failure = runCatching { load(dest) }.exceptionOrNull()
            if (generation != loadGeneration) return@launch
            loading = false
            error = failure?.message
        }
    }

    fun submitSearch() {
        val text = query.trim()
        if (current !is Dest.Search) stack = listOf(Dest.Search)
        if (text.isNotBlank()) search(text)
    }

    fun search(text: String = query) {
        query = text
        scope.launch {
            loading = true
            val result = withContext(Dispatchers.IO) { YouTube.search(text, searchFilter) }
            loading = false
            result.onSuccess {
                searchItems = it.items
                searchContinuation = it.continuation
                error = null
            }.onFailure { error = it.message }
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
        scope.launch {
            val result = withContext(Dispatchers.IO) { YouTube.searchContinuation(token) }
            result.onSuccess {
                searchItems = searchItems + it.items
                searchContinuation = it.continuation
            }
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

    fun loadMorePlaylist() {
        val token = playlistContinuation ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) { YouTube.playlistContinuation(token) }
            result.onSuccess { page ->
                playlist = playlist?.copy(songs = playlist?.songs.orEmpty() + page.songs)
                playlistContinuation = page.continuation
            }
        }
    }

    fun selectLibrary(tab: LibraryTab) {
        libraryTab = tab
        refresh()
    }

    fun loadMoreLibrary() {
        val token = libraryContinuation ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) { YouTube.libraryContinuation(token) }
            result.onSuccess { page ->
                libraryItems = libraryItems + page.items
                libraryContinuation = page.continuation
            }
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
        val added = store.toggleLike(track.toSaved())
        liked = store.library.liked
        status = if (added) "Beğenildi" else "Beğeni kaldırıldı"
        if (track.localPath == null) libraryTools.queueSync(SyncAction("like", value = track.id, enabled = added))
    }

    fun prepareAdd() {
        val track = player.current ?: return
        if (localPlaylists.isEmpty()) {
            status = "Önce Kitaplık’tan bir liste oluştur"
            return
        }
        showAdd = true
        if (store.settings.cookie.isBlank()) return
        scope.launch {
            val page = withContext(Dispatchers.IO) { YouTube.library("FEmusic_liked_playlists").getOrNull() }
            val remote = page?.items?.filterIsInstance<PlaylistItem>().orEmpty()
            if (player.current?.id == track.id) playlistsForAdd = remote
        }
    }

    fun addCurrentTo(playlistId: String) {
        val track = player.current ?: return
        if (playlistId.startsWith("local-")) {
            libraryTools.addTracks(playlistId, listOf(track.toSaved()))
            localPlaylists = store.library.playlists
            status = "Listeye eklendi"
            showAdd = false
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) { YouTube.addToPlaylist(playlistId, track.id) }
            status = if (result.isSuccess) "YouTube listesine eklendi" else result.exceptionOrNull()?.message
            showAdd = false
        }
    }

    fun createPlaylist() {
        val title = newPlaylistTitle.trim()
        if (title.isBlank()) {
            status = "Liste adı yaz"
            return
        }
        libraryTools.createPlaylist(title)
        localPlaylists = store.library.playlists
        newPlaylistTitle = ""
        showCreate = false
        status = "Liste oluşturuldu"
    }

    fun openLocal(id: String) {
        open(Dest.LocalList(id))
    }

    private fun startLiveFeed() {
        scope.launch {
            while (true) {
                delay(45_000)
                when (current) {
                    Dest.Explore -> loadExplore()
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
    private val seenHome = mutableSetOf<String>()

    private suspend fun appendHome() {
        val token = homeContinuation
        if (!token.isNullOrBlank()) {
            val page = withContext(Dispatchers.IO) { YouTube.home(continuation = token).getOrNull() }
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
        if (found.isEmpty()) return
        val title = when (filter) {
            YouTube.SearchFilter.FILTER_ALBUM -> "$query albümleri"
            YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST -> "$query listeleri"
            else -> "$query"
        }
        addHomeSections(listOf(HomePage.Section(title, null, null, null, found)))
    }

    private fun addHomeSections(incoming: List<HomePage.Section>) {
        val current = home ?: return
        val fresh = incoming.filter { section ->
            val key = section.title + section.items.take(4).joinToString { it.id }
            seenHome.add(key)
        }
        if (fresh.isEmpty()) return
        home = current.copy(sections = current.sections + fresh)
    }

    fun saveCookie() {
        val cookie = cookieDraft.trim()
        store.saveSettings(store.settings.copy(cookie = cookie))
        applyCookie(cookie)
        status = if (cookie.isBlank()) "Çıkış yapıldı" else "Çerez kaydedildi"
        accountName = null
        refresh()
    }

    fun saveQuality(quality: String) {
        this.quality = quality
        store.saveSettings(store.settings.copy(quality = quality))
    }

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

    internal fun setScrollVolume(on: Boolean) {
        shortcuts = shortcuts.copy(scrollVolume = on)
        saveShortcuts()
    }

    internal fun setShortcutEnabled(slot: ShortcutSlot, on: Boolean) {
        shortcuts = shortcuts.setEnabled(slot, on)
        saveShortcuts()
    }

    internal fun assignShortcut(slot: ShortcutSlot, key: String) {
        shortcuts = shortcuts.assign(slot, key)
        shortcutCapture = null
        saveShortcuts()
    }

    internal fun clearShortcut(slot: ShortcutSlot) {
        shortcuts = shortcuts.setKey(slot, "")
        saveShortcuts()
    }

    internal fun resetShortcuts() {
        shortcuts = ShortcutBook()
        shortcutCapture = null
        saveShortcuts()
    }

    private fun saveShortcuts() {
        store.saveSettings(store.settings.copy(shortcuts = shortcuts))
    }

    fun saveCaptionStop(start: Boolean, hex: String) {
        if (start) captionStart = hex else captionEnd = hex
        caption = "custom"
        saveChrome()
    }

    private fun saveChrome() {
        store.saveSettings(
            store.settings.copy(
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
            ),
        )
    }

    internal fun saveLook(
        accent: String = look.accentId,
        surface: String = look.surfaceId,
        corners: String = look.cornerId,
        cards: String = look.cardId,
    ) {
        look = Look(accent, surface, corners, cards)
        store.saveSettings(
            store.settings.copy(
                accent = accent,
                surface = surface,
                corners = corners,
                cards = cards,
            ),
        )
    }

    private fun applyCookie(cookie: String) {
        YouTube.cookie = cookie.ifBlank { null }
        YouTube.useLoginForBrowse = cookie.isNotBlank()
    }

    private suspend fun load(dest: Dest) {
        when (dest) {
            Dest.Home -> withContext(Dispatchers.IO) { YouTube.home() }.getOrThrow().also {
                home = it
                homeContinuation = it.continuation
            }
            Dest.Search -> Unit
            Dest.Library -> loadLibrary()
            Dest.History -> {
                history = store.history
                if (store.settings.cookie.isNotBlank()) {
                    remoteHistory = withContext(Dispatchers.IO) { YouTube.musicHistory().getOrNull()?.sections.orEmpty() }
                }
            }
            Dest.Explore -> loadExplore()
            Dest.Settings -> {
                if (store.settings.cookie.isNotBlank()) {
                    accountName = withContext(Dispatchers.IO) { YouTube.accountInfo().getOrNull()?.name }
                }
            }
            Dest.Liked -> liked = store.library.liked
            Dest.Shortcuts, Dest.LocalMusic, Dest.Insights -> Unit
            is Dest.Smart -> Unit
            is Dest.LocalList -> Unit
            is Dest.Album -> album = withContext(Dispatchers.IO) { YouTube.album(dest.browseId).getOrThrow() }
            is Dest.Artist -> artist = withContext(Dispatchers.IO) { YouTube.artist(dest.browseId).getOrThrow() }
            is Dest.Playlist -> {
                val page = withContext(Dispatchers.IO) { YouTube.playlist(dest.playlistId).getOrThrow() }
                playlist = page
                playlistContinuation = page.songsContinuation
            }
            is Dest.Collection -> collection = withContext(Dispatchers.IO) {
                YouTube.browse(dest.browseId, dest.params).getOrThrow()
            }
        }
    }

    private suspend fun loadLibrary() {
        if (store.settings.cookie.isBlank()) {
            libraryItems = emptyList()
            libraryContinuation = null
            return
        }
        val page = withContext(Dispatchers.IO) { YouTube.library(libraryTab.browseId).getOrThrow() }
        libraryItems = page.items
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
            explore = exploreCall.await().getOrNull()
            charts = chartsCall.await().getOrNull()
            moods = moodsCall.await().getOrNull().orEmpty()
            releases = releasesCall.await().getOrNull().orEmpty()
        }
    }
}
