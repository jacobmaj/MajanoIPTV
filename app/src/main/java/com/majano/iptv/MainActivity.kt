package com.majano.iptv

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

class MainActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var model: AppModel? = null

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        // FFmpeg como respaldo: si el teléfono no decodifica el audio (AC3/EAC3/DTS...), lo hace por software.
        val renderers = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        val exo = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setAudioAttributes(audioAttrs, true)
            .build()
        exo.volume = 1f
        player = exo
        val m = AppModel(Store(applicationContext), lifecycleScope, exo)
        model = m

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) {
                IPTVRoot(m) { on -> setFullscreenMode(on) }
            }
        }
    }

    private fun setFullscreenMode(on: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    /**
     * Control remoto: en pantalla completa las teclas se procesan aquí (antes que cualquier vista),
     * así siempre funcionan: OK = pausa, ◀ ▶ = adelantar/retroceder, ▲ ▼ = canal, Atrás = salir.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val m = model
        if (m != null && event.action == KeyEvent.ACTION_DOWN && !m.tracksOpen) {
            if (m.fullscreen) {
                if (m.handleKey(event.keyCode, event.repeatCount)) return true
            } else if (m.loggedIn) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (m.zap(1)) return true
                    }
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (m.zap(-1)) return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (m.hasMedia) {
                            m.togglePlay()
                            return true
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        model?.saveProgress()
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
