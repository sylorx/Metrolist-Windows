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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppModel {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val store = Store()
    private val resolver = StreamResolver(appDir())
    internal val updates = Updater(scope)
    val player = PlaybackController(
        resolver = resolver,
        scope = scope,
        quality = { store.settings.quality },
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
    var status by mutableStateOf<String?>(null)
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
    var minimizeToTray by mutableStateOf(store.settings.minimizeToTray)
        private set
    var playlistsForAdd by mutableStateOf<List<PlaylistItem>>(emptyList())
    var showAdd by mutableStateOf(false)
    var showCreate by mutableStateOf(false)
    var newPlaylistTitle by mutableStateOf("")
    private var loadGeneration by mutableIntStateOf(0)
    private var suggestJob: Job? = null
    private var openedHome = false

    val current: Dest get() = stack.last()

    init {
        applyCookie(store.settings.cookie)
        updates.start()
        startLiveFeed()
        scope.launch {
            while (isActive) {
                delay(300)
                player.poll()
            }
        }
    }

    fun openInitialHome() {
        if (openedHome) return
        openedHome = true
        openRail(Dest.Home)
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
        if (added && store.settings.cookie.isNotBlank()) {
            scope.launch(Dispatchers.IO) { runCatching { YouTube.likeVideo(track.id, true) } }
        }
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
            store.addToPlaylist(playlistId, track.toSaved())
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
        store.createPlaylist(title)
        localPlaylists = store.library.playlists
        newPlaylistTitle = ""
        showCreate = false
        status = "Liste oluşturuldu"
        if (store.settings.cookie.isNotBlank()) {
            scope.launch(Dispatchers.IO) { runCatching { YouTube.createPlaylist(title) } }
        }
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

    fun saveMinimizeToTray(enabled: Boolean) {
        minimizeToTray = enabled
        store.saveSettings(store.settings.copy(minimizeToTray = enabled))
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
            Dest.Shortcuts -> Unit
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
