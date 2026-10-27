package com.majano.iptv

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppModel(
    private val store: Store,
    private val scope: CoroutineScope,
    val player: ExoPlayer,
) {
    var loggedIn by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var message by mutableStateOf("")
    var messageIsError by mutableStateOf(false)
    var accountInfo by mutableStateOf("")
    var session: Session? = null
        private set

    val savedLink: String get() = store.link
    val savedUser: String get() = store.user

    val cats = mutableStateMapOf<Kind, List<Category>>()
    val items = mutableStateMapOf<Kind, List<Entry>>()
    private val loadingKinds = mutableStateListOf<Kind>()
    fun isLoading(k: Kind): Boolean = loadingKinds.contains(k)

    val favorites = mutableStateListOf<String>().apply { addAll(store.favorites()) }

    // ───── Historial ("Continuar viendo") ─────
    val history = mutableStateListOf<Watch>().apply { addAll(decodeHistory(store.historyJson())) }

    // ───── Reproductor ─────
    var nowTitle by mutableStateOf("")
    var current by mutableStateOf<Entry?>(null)
        private set
    var hasMedia by mutableStateOf(false)
    var isBuffering by mutableStateOf(false)
    var playerError by mutableStateOf("")
    var fullscreen by mutableStateOf(false)
    var isLive by mutableStateOf(false)
        private set
    var wantsPlay by mutableStateOf(true)
        private set
    var overlayVisible by mutableStateOf(false)
        private set
    var tracksOpen by mutableStateOf(false)

    // Siguiente episodio (aparece cerca de los créditos)
    var nextUp by mutableStateOf<Episode?>(null)
        private set
    var nextDismissed by mutableStateOf(false)
    var nextCountdown by mutableStateOf(0)
        private set
    // Saltar intro (opening)
    var introSkipSec by mutableStateOf(store.introSkipSec)
        private set
    var introAvailable by mutableStateOf(false)
        private set
    private var introUsedFor = ""
    private var playing: Entry? = null
    private var playingEp: Episode? = null
    private var playingEps: List<Episode> = emptyList()
    private var pendingResume: Watch? = null

    private var queue: List<Entry> = emptyList()
    private var queueIndex = -1
    private var altTried = false
    private var currentUrl = ""
    private var overlayJob: Job? = null

    // ───── Detalle (película / serie) ─────
    var detail by mutableStateOf<Entry?>(null)
        private set
    var info by mutableStateOf<Info?>(null)
        private set
    var infoLoading by mutableStateOf(false)
        private set
    var episodes by mutableStateOf<List<Episode>>(emptyList())
        private set
    var detailSeason by mutableStateOf(1)

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED && hasMedia && !isLive) scope.launch { handleEnded() }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                wantsPlay = playWhenReady
            }

            override fun onPlayerError(error: PlaybackException) {
                val alt = if (!altTried) altUrl(currentUrl) else null
                if (alt != null && isLive) {
                    altTried = true
                    currentUrl = alt
                    player.setMediaItem(MediaItem.fromUri(alt))
                    player.prepare()
                    player.playWhenReady = true
                    return
                }
                playerError = "No se pudo reproducir (${error.errorCodeName}). El canal puede estar caído o tu plan no permite esta salida."
            }
        })
        scope.launch {
            var tick = 0
            while (true) {
                delay(1000)
                if (!hasMedia || isLive) { tick = 0; continue }
                updateNextUp()
                updateIntro()
                tick++
                if (tick % 10 == 0 && player.isPlaying) saveProgress()
            }
        }
        if (store.auto && store.link.isNotBlank() && (store.pass.isNotBlank() || parseLink(store.link)?.pass != null)) {
            login(store.link, store.user, store.pass)
        }
    }

    private fun altUrl(url: String): String? = when {
        url.endsWith(".m3u8") -> url.dropLast(5) + ".ts"
        url.endsWith(".ts") -> url.dropLast(3) + ".m3u8"
        else -> null
    }

    private fun setInfo(text: String) { message = text; messageIsError = false }
    private fun setError(text: String) { message = text; messageIsError = true }

    // ───── Sesión ─────
    fun login(link: String, user: String, pass: String) {
        if (busy) return
        busy = true
        setInfo("Conectando con el proveedor…")
        scope.launch {
            val r: ConnectResult = try {
                withContext(Dispatchers.IO) { Provider.connect(link, user, pass) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ConnectResult.Fail(Net.describe(e))
            }
            when (r) {
                is ConnectResult.Fail -> {
                    setError(r.reason)
                    busy = false
                }
                is ConnectResult.Ok -> {
                    store.saveLogin(link, user, pass)
                    resetCatalog()
                    session = r.session
                    accountInfo = r.info
                    val playlist = r.playlist
                    if (playlist != null) applyPlaylist(playlist)
                    loggedIn = true
                    busy = false
                    setInfo("")
                    loadKind(Kind.LIVE)
                }
            }
        }
    }

    private fun applyPlaylist(list: List<Entry>) {
        for (k in Kind.values()) {
            val l = list.filter { it.kind == k }
            items[k] = l
            cats[k] = l.map { it.categoryId }.distinct().map { Category(it, it) }
        }
    }

    private fun resetCatalog() {
        cats.clear()
        items.clear()
        loadingKinds.clear()
        detail = null
        info = null
        episodes = emptyList()
        stopPlayback()
    }

    fun stopPlayback() {
        saveProgress()
        player.stop()
        player.clearMediaItems()
        hasMedia = false
        fullscreen = false
        playerError = ""
        nowTitle = ""
        current = null
        isLive = false
        overlayVisible = false
        tracksOpen = false
        playing = null
        playingEp = null
        nextUp = null
        nextDismissed = false
        queue = emptyList()
        queueIndex = -1
    }

    fun logout() {
        store.clearLogin()
        resetCatalog()
        session = null
        loggedIn = false
        setInfo("")
    }

    fun loadKind(kind: Kind, force: Boolean = false) {
        val s = session ?: return
        if (!force && items.containsKey(kind)) return
        if (loadingKinds.contains(kind)) return
        if (!s.isXtream) {
            if (force) { login(store.link, store.user, store.pass); return }
            if (!items.containsKey(kind)) items[kind] = emptyList()
            return
        }
        loadingKinds.add(kind)
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    Pair(Provider.categories(s, kind), Provider.entries(s, kind))
                }
                cats[kind] = result.first
                items[kind] = result.second
                if (result.second.isEmpty()) setInfo("El proveedor no devolvió contenido en esta sección.") else setInfo("")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setError(Net.describe(e))
            } finally {
                loadingKinds.remove(kind)
            }
        }
    }

    // ───── Favoritos ─────
    private fun key(e: Entry) = "${e.kind.name}:${e.id}"
    fun isFav(e: Entry): Boolean = favorites.contains(key(e))
    fun toggleFav(e: Entry) {
        val k = key(e)
        if (!favorites.remove(k)) favorites.add(k)
        store.saveFavorites(favorites.toSet())
    }

    // ───── Reproducción ─────
    /** Tocar un elemento: canal → reproduce (o pantalla completa si ya suena); película/serie → pantalla de detalle. */
    fun open(e: Entry, from: List<Entry>, immersive: Boolean = false) {
        when {
            e.kind == Kind.LIVE -> {
                if (hasMedia && isLive && current === e) {
                    enterFullscreen()
                    return
                }
                queue = from
                queueIndex = from.indexOfFirst { it === e }
                playUrl(e.name, e.url, true, e)
                if (immersive) enterFullscreen()
            }
            e.kind == Kind.SERIES && e.url.isNotBlank() -> playMovie(e)
            else -> openDetail(e)
        }
    }

    fun playUrl(title: String, url: String, live: Boolean, entry: Entry? = null, startMs: Long = 0L) {
        saveProgress()
        playing = null
        playingEp = null
        nextUp = null
        nextDismissed = false
        introAvailable = false
        nowTitle = title
        current = entry
        currentUrl = url
        isLive = live
        altTried = false
        playerError = ""
        hasMedia = true
        player.setMediaItem(MediaItem.fromUri(url), startMs)
        player.prepare()
        player.playWhenReady = true
        if (fullscreen) pokeOverlay()
    }

    fun playMovie(e: Entry, startMs: Long = 0L) {
        queue = emptyList()
        queueIndex = -1
        playUrl(e.name, e.url, false, e, startMs)
        playing = e
        playingEp = null
        enterFullscreen()
    }

    fun playEpisode(series: Entry, ep: Episode, startMs: Long = 0L) {
        queue = emptyList()
        queueIndex = -1
        if (episodes.isNotEmpty()) playingEps = episodes
        detailSeason = ep.season
        playUrl("${series.name} · T${ep.season} E${ep.number}", ep.url, false, null, startMs)
        playing = series
        playingEp = ep
        val s = session
        if (s != null) {
            upsertWatch(Watch(watchKey(series), s.base, series, ep.id, ep.season, ep.number, ep.title, startMs, 0L, System.currentTimeMillis()))
        }
        enterFullscreen()
    }

    fun playNextEpisode() {
        val ep = playingEp ?: return
        val series = playing ?: return
        val next = nextEpisodeAfter(ep) ?: return
        playEpisode(series, next, 0L)
    }

    private fun nextEpisodeAfter(ep: Episode): Episode? {
        val i = playingEps.indexOfFirst { it.id == ep.id }
        return if (i >= 0 && i + 1 < playingEps.size) playingEps[i + 1] else null
    }

    private fun updateNextUp() {
        val ep = playingEp
        if (ep == null) {
            if (nextUp != null) nextUp = null
            return
        }
        val dur = player.duration
        if (dur <= 0L) return
        val remaining = dur - player.currentPosition
        val next = nextEpisodeAfter(ep)
        if (next != null && dur > 300_000L && remaining in 1L..45_000L) {
            if (nextUp == null) {
                nextUp = next
                nextDismissed = false
            }
            nextCountdown = (remaining / 1000L).toInt()
        } else if (nextUp != null && remaining > 50_000L) {
            nextUp = null
        }
    }

    private fun updateIntro() {
        val ep = playingEp
        val show = ep != null && introUsedFor != ep.id && nextUp == null &&
            player.currentPosition < 240_000L && player.duration > 600_000L
        if (show != introAvailable) introAvailable = show
    }

    /** Salta el opening: avanza [introSkipSec] segundos (los episodios no traen marcas de intro). */
    fun skipIntro() {
        val ep = playingEp ?: return
        seekBy(introSkipSec * 1000L)
        introUsedFor = ep.id
        introAvailable = false
        pokeOverlay()
    }

    fun setIntroSkip(sec: Int) {
        introSkipSec = sec
        store.introSkipSec = sec
    }

    /** Terminó el video: pasa al siguiente episodio o sale si ya no hay más. */
    private fun handleEnded() {
        if (!hasMedia || isLive) return
        val ep = playingEp
        if (ep != null && nextEpisodeAfter(ep) != null) {
            playNextEpisode()
            return
        }
        if (fullscreen) exitFullscreen() else stopPlayback()
    }

    // ───── Historial ─────
    private fun watchKey(e: Entry) = "${e.kind.name}:${e.id}"

    fun watchFor(e: Entry): Watch? {
        val s = session ?: return null
        val k = watchKey(e)
        return history.firstOrNull { it.key == k && it.server == s.base }
    }

    private fun persistHistory() {
        store.saveHistoryJson(encodeHistory(history.toList()))
    }

    private fun upsertWatch(w: Watch) {
        history.removeAll { it.key == w.key && it.server == w.server }
        history.add(0, w)
        while (history.size > 60) history.removeAt(history.size - 1)
        persistHistory()
    }

    private fun dropWatch(key: String, server: String) {
        history.removeAll { it.key == key && it.server == server }
        persistHistory()
    }

    fun removeWatch(w: Watch) = dropWatch(w.key, w.server)

    fun clearHistory() {
        val s = session ?: return
        history.removeAll { it.server == s.base }
        persistHistory()
    }

    /** Guarda dónde vas (se llama cada 10 s, al salir y al cerrar la app). */
    fun saveProgress() {
        val s = session ?: return
        val e = playing ?: return
        if (!hasMedia || isLive) return
        val dur = player.duration
        if (dur <= 0L) return
        val pos = player.currentPosition
        val key = watchKey(e)
        val ep = playingEp
        val finished = dur - pos < 90_000L || pos.toFloat() / dur.toFloat() > 0.93f
        if (finished) {
            val next = if (ep != null) nextEpisodeAfter(ep) else null
            if (next != null) {
                upsertWatch(Watch(key, s.base, e, next.id, next.season, next.number, next.title, 0L, 0L, System.currentTimeMillis()))
            } else {
                dropWatch(key, s.base)
            }
            return
        }
        if (pos < 30_000L) return
        if (ep != null) {
            upsertWatch(Watch(key, s.base, e, ep.id, ep.season, ep.number, ep.title, pos, dur, System.currentTimeMillis()))
        } else {
            upsertWatch(Watch(key, s.base, e, positionMs = pos, durationMs = dur, updatedAt = System.currentTimeMillis()))
        }
    }

    /** Continuar viendo: película → reproduce donde la dejó; serie → abre en su temporada y reproduce el episodio. */
    fun resume(w: Watch) {
        val e = w.entry
        val start = maxOf(0L, w.positionMs - 5_000L)
        if (e.kind == Kind.MOVIE || e.url.isNotBlank()) {
            playMovie(e, start)
            return
        }
        pendingResume = w
        openDetail(e)
    }

    fun enterFullscreen() {
        if (!hasMedia) return
        fullscreen = true
        pokeOverlay()
    }

    fun exitFullscreen() {
        overlayJob?.cancel()
        overlayVisible = false
        // Películas y episodios se detienen al salir (y se guarda dónde ibas); el canal en vivo sigue en la vista previa.
        if (hasMedia && !isLive) stopPlayback() else fullscreen = false
    }

    fun pokeOverlay() {
        overlayVisible = true
        overlayJob?.cancel()
        overlayJob = scope.launch {
            delay(4500)
            if (wantsPlay) overlayVisible = false
        }
    }

    fun toggleOverlay() {
        if (overlayVisible) {
            overlayJob?.cancel()
            overlayVisible = false
        } else {
            pokeOverlay()
        }
    }

    fun togglePlay() {
        if (!hasMedia) return
        player.playWhenReady = !player.playWhenReady
    }

    fun seekBy(deltaMs: Long) {
        if (!hasMedia || isLive) return
        val target = player.currentPosition + deltaMs
        val d = player.duration
        player.seekTo(if (d > 0) target.coerceIn(0L, d) else target.coerceAtLeast(0L))
    }

    /** Cambio de canal (CH+/CH-, flechas en pantalla completa). */
    fun zap(delta: Int): Boolean {
        if (!loggedIn || !hasMedia || !isLive || queue.isEmpty() || queueIndex < 0) return false
        val n = queue.size
        queueIndex = ((queueIndex + delta) % n + n) % n
        val e = queue[queueIndex]
        playUrl(e.name, e.url, true, e)
        return true
    }

    /** Teclas del control remoto en pantalla completa. Devuelve true si la tecla se usó. */
    fun handleKey(code: Int, repeat: Int): Boolean {
        if (!hasMedia) return false
        if (nextUp != null && !nextDismissed) {
            when (code) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    if (repeat == 0) playNextEpisode()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    nextDismissed = true
                    return true
                }
            }
        }
        if (introAvailable && code == KeyEvent.KEYCODE_DPAD_UP) {
            skipIntro()
            return true
        }
        when (code) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (repeat == 0) { togglePlay(); pokeOverlay() }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { player.playWhenReady = true; pokeOverlay(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.playWhenReady = false; pokeOverlay(); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isLive) { if (repeat == 0) tracksOpen = true } else { seekBy(-10_000L); pokeOverlay() }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isLive) pokeOverlay() else { seekBy(10_000L); pokeOverlay() }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-30_000L); pokeOverlay(); return true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(30_000L); pokeOverlay(); return true }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isLive) { if (repeat == 0) zap(1) } else pokeOverlay()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isLive) { if (repeat == 0) zap(-1) } else if (repeat == 0) tracksOpen = true
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (isLive) { if (repeat == 0) zap(1) } else { seekBy(60_000L); pokeOverlay() }
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (isLive) { if (repeat == 0) zap(-1) } else { seekBy(-60_000L); pokeOverlay() }
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_CAPTIONS -> {
                if (repeat == 0) tracksOpen = true
                return true
            }
        }
        return false
    }

    // ───── Detalle ─────
    fun openDetail(e: Entry) {
        detail = e
        info = null
        episodes = emptyList()
        detailSeason = (watchFor(e)?.season ?: 0).let { if (it > 0) it else 1 }
        val s = session
        if (s == null || !s.isXtream) {
            infoLoading = false
            return
        }
        infoLoading = true
        scope.launch {
            try {
                if (e.kind == Kind.SERIES) {
                    val data = withContext(Dispatchers.IO) { Provider.seriesData(s, e.id) }
                    if (detail === e) {
                        info = data.first
                        episodes = data.second
                        if (data.second.isEmpty()) setError("Esta serie no tiene episodios disponibles.") else setInfo("")
                        val pr = pendingResume
                        pendingResume = null
                        if (pr != null && pr.entry.id == e.id) {
                            val ep = data.second.firstOrNull { it.id == pr.episodeId }
                            if (ep != null) playEpisode(e, ep, maxOf(0L, pr.positionMs - 5_000L))
                        }
                    }
                } else if (e.kind == Kind.MOVIE) {
                    val i = withContext(Dispatchers.IO) { Provider.vodInfo(s, e.id) }
                    if (detail === e) info = i
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                if (e.kind == Kind.SERIES) setError(Net.describe(ex))
            } finally {
                if (detail === e) infoLoading = false
            }
        }
    }

    fun closeDetail() {
        detail = null
        info = null
        episodes = emptyList()
        infoLoading = false
    }
}
