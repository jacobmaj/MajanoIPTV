@file:OptIn(ExperimentalMaterial3Api::class)

package com.majano.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "Continuar viendo": películas y series que dejaste a medias, con la barra de progreso. */
@Composable
fun HistoryScreen(model: AppModel) {
    val server = model.session?.base.orEmpty()
    val list = model.history.filter { it.server == server }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Continuar viendo", color = MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Retoma justo donde lo dejaste", color = Muted, fontSize = 12.sp)
            }
            if (list.isNotEmpty()) Btn("Borrar historial", { model.clearHistory() })
        }
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) {
            EmptyBox("Aquí aparecerá lo que veas.\nPodrás continuar películas y series justo donde las dejaste.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(124.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(6.dp),
            ) {
                items(list) { w -> ContinueCard(model, w) }
            }
        }
    }
}

private fun subtitleOf(w: Watch): String {
    return if (w.entry.kind == Kind.SERIES && w.episodeId.isNotBlank()) {
        val pct = if (w.durationMs > 0) " · ${(w.positionMs * 100 / w.durationMs).toInt()}%" else ""
        "T${w.season} · E${w.number}$pct"
    } else if (w.durationMs > 0) {
        val left = ((w.durationMs - w.positionMs) / 60000L).toInt().coerceAtLeast(1)
        "Quedan " + (if (left >= 60) "${left / 60} h ${left % 60} min" else "$left min")
    } else {
        "Continuar"
    }
}

@Composable
private fun ContinueCard(model: AppModel, w: Watch) {
    val e = w.entry
    Focusable(
        onClick = { model.resume(w) },
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        focusScale = 1.05f,
    ) {
        Column {
            Box {
                PosterImage(e.image, e.name, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)))
                if (w.durationMs > 0) {
                    val frac = (w.positionMs.toFloat() / w.durationMs.toFloat()).coerceIn(0f, 1f)
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x99000000))) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Accent))
                    }
                }
                Text(
                    "✕", color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .background(Color(0x99000000), CircleShape)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                        .pointerInput(w) { detectTapGestures { model.removeWatch(w) } },
                )
            }
            Text(
                e.name, color = SoftText, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
            )
            Text(subtitleOf(w), color = Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
        }
    }
}
