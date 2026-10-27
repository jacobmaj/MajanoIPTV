@file:OptIn(ExperimentalMaterial3Api::class)

package com.majano.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** Solo el video (sin controles propios de Media3; los controles los dibujamos nosotros). */
@Composable
fun PlayerBox(model: AppModel, modifier: Modifier) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = model.player
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (model.isBuffering && model.playerError.isBlank()) CircularProgressIndicator(color = Accent)
        if (!model.hasMedia) Text("Elige algo para reproducir", color = Muted, fontSize = 13.sp)
        if (model.playerError.isNotBlank()) {
            Text(model.playerError, color = ErrorColor, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
fun PlayerPhone(model: AppModel) {
    Column(Modifier.fillMaxWidth()) {
        PlayerBox(model, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Row(
            Modifier.fillMaxWidth().background(Bg).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                model.nowTitle, color = MainText, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Btn("CC", { model.tracksOpen = true })
            Btn("Pantalla completa", { model.enterFullscreen() }, primary = true)
        }
    }
}

@Composable
fun FullPlayer(model: AppModel) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PlayerBox(model, Modifier.fillMaxSize())
        PlayerOverlay(model)
        NextUpCard(model, Modifier.align(Alignment.BottomEnd))
        SkipIntroButton(model, Modifier.align(Alignment.BottomEnd))
    }
}

/** Botón "Saltar intro" durante los primeros minutos de un episodio. */
@Composable
private fun SkipIntroButton(model: AppModel, modifier: Modifier) {
    if (!model.introAvailable) return
    val wide = LocalConfiguration.current.screenWidthDp >= 720
    Column(modifier.padding(end = 24.dp, bottom = 96.dp), horizontalAlignment = Alignment.End) {
        Btn("Saltar intro ▶▶", { model.skipIntro() }, primary = true)
        if (wide) Text("▲ Saltar intro", color = SoftText, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Aparece en los últimos segundos (créditos): sugiere el siguiente episodio. */
@Composable
private fun NextUpCard(model: AppModel, modifier: Modifier) {
    val next = model.nextUp
    if (next == null || model.nextDismissed) return
    val wide = LocalConfiguration.current.screenWidthDp >= 720
    Surface(
        modifier = modifier.padding(end = 24.dp, bottom = 96.dp).width(300.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xE6151923),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("SIGUIENTE EPISODIO", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(
                "T${next.season} · E${next.number}  ${next.title}", color = Color.White, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text("Empieza en ${model.nextCountdown} s", color = SoftText, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("▶ Reproducir ahora", { model.playNextEpisode() }, Modifier.weight(1f), primary = true)
                Btn("Ocultar", { model.nextDismissed = true })
            }
            if (wide) Text("OK Reproducir ahora      ▲ Ocultar", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RoundBtn(text: String, onClick: () -> Unit) {
    Focusable(onClick = onClick, shape = CircleShape, color = Color(0xAA000000)) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlayerOverlay(model: AppModel) {
    val visible = model.overlayVisible
    val live = model.isLive
    val wide = LocalConfiguration.current.screenWidthDp >= 720
    var pos by remember { mutableStateOf(0L) }
    var dur by remember { mutableStateOf(0L) }

    LaunchedEffect(visible) {
        while (visible) {
            pos = model.player.currentPosition
            dur = model.player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { model.toggleOverlay() } },
    ) {
        if (visible) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)))

            // Arriba: título y salir
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (live) {
                    Text("● EN VIVO", color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Text(
                    model.nowTitle, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Btn("CC", { model.tracksOpen = true })
                Btn("Salir", { model.exitFullscreen() })
            }

            // Centro: botones táctiles
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (!live) RoundBtn("−10 s") { model.seekBy(-10_000L); model.pokeOverlay() }
                RoundBtn(if (model.wantsPlay) "❚❚" else "▶") { model.togglePlay(); model.pokeOverlay() }
                if (!live) RoundBtn("+10 s") { model.seekBy(10_000L); model.pokeOverlay() }
            }

            // Abajo: barra de progreso y ayuda del control remoto
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                if (!live && dur > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtTime(pos), color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = pos.toFloat().coerceIn(0f, dur.toFloat()),
                            onValueChange = { v ->
                                pos = v.toLong()
                                model.player.seekTo(pos)
                                model.pokeOverlay()
                            },
                            valueRange = 0f..dur.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text(fmtTime(dur), color = Color.White, fontSize = 12.sp)
                    }
                }
                if (wide) {
                    Text(
                        if (live) "▲ ▼ Cambiar canal      ◀ Audio y subtítulos      OK Pausa / Reanudar      Atrás: salir"
                        else "◀ ▶ Retroceder / adelantar 10 s      ▼ Audio y subtítulos      OK Pausa / Reanudar      Atrás: salir",
                        color = SoftText, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
