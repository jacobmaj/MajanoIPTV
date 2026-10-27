@file:OptIn(ExperimentalMaterial3Api::class)

package com.majano.iptv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import java.util.Locale

private class TrackOption(
    val label: String,
    val selected: Boolean,
    val group: Tracks.Group,
    val index: Int,
)

private fun optionsOf(tracks: Tracks, type: Int): List<TrackOption> {
    val out = ArrayList<TrackOption>()
    for (g in tracks.groups) {
        if (g.type != type) continue
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            val f = g.getTrackFormat(i)
            val name = f.label ?: f.language?.let { Locale.forLanguageTag(it).displayLanguage }
            val label = name.takeUnless { it.isNullOrBlank() } ?: "Pista ${out.size + 1}"
            out.add(TrackOption(label, g.isTrackSelected(i), g, i))
        }
    }
    return out
}

/** Ventana "Audio y subtítulos": desactivar/activar subtítulos y cambiar de pista de audio. */
@Composable
fun TrackDialog(player: ExoPlayer, onDismiss: () -> Unit) {
    var tracks by remember { mutableStateOf(player.currentTracks) }
    var params by remember { mutableStateOf(player.trackSelectionParameters) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onTrackSelectionParametersChanged(p: TrackSelectionParameters) { params = p }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val subsOff = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    val subs = optionsOf(tracks, C.TRACK_TYPE_TEXT)
    val audio = optionsOf(tracks, C.TRACK_TYPE_AUDIO)

    fun disableSubs() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    fun choose(type: Int, o: TrackOption) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index))
            .build()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        title = { Text("Audio y subtítulos") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Subtítulos", fontWeight = FontWeight.Bold)
                OptionRow("Desactivados", subsOff || subs.none { it.selected }) { disableSubs() }
                subs.forEach { o ->
                    OptionRow(o.label, !subsOff && o.selected) { choose(C.TRACK_TYPE_TEXT, o) }
                }
                if (subs.isEmpty()) Text("Este contenido no trae subtítulos.", fontSize = 12.sp, color = Muted)
                if (audio.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Text("Audio", fontWeight = FontWeight.Bold)
                    audio.forEach { o -> OptionRow(o.label, o.selected) { choose(C.TRACK_TYPE_AUDIO, o) } }
                }
            }
        },
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, fontSize = 14.sp)
    }
}
