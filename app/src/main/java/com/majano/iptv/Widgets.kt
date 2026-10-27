@file:OptIn(ExperimentalMaterial3Api::class)

package com.majano.iptv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

val Bg = Color(0xFF090B10)
val Panel = Color(0xFF151923)
val PanelAlt = Color(0xFF1F2535)
val Accent = Color(0xFFE50914)
val MainText = Color(0xFFF5F6F8)
val SoftText = Color(0xFFD1D5DB)
val Muted = Color(0xFF9CA3AF)
val ErrorColor = Color(0xFFFF6B6B)

/** Contenedor que se puede seleccionar con el control remoto (borde blanco al enfocar). */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    color: Color = Panel,
    shape: Shape = RoundedCornerShape(10.dp),
    focusScale: Float = 1f,
    focusRequester: FocusRequester? = null,
    onFocus: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val onFocusState by rememberUpdatedState(onFocus)
    LaunchedEffect(focused) {
        if (focused) onFocusState?.invoke()
    }
    var mod = modifier
    if (focusRequester != null) mod = mod.focusRequester(focusRequester)
    if (focusScale != 1f && focused) mod = mod.scale(focusScale)
    Surface(
        onClick = onClick,
        modifier = mod,
        shape = shape,
        color = if (selected) Color(0xFF2A1F2B) else color,
        border = if (focused) BorderStroke(2.dp, Color.White)
        else if (selected) BorderStroke(1.dp, Accent.copy(alpha = 0.6f))
        else null,
        interactionSource = interaction,
        content = content,
    )
}

@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    Focusable(
        onClick = onClick,
        modifier = modifier,
        color = if (primary) Accent else PanelAlt,
        shape = RoundedCornerShape(8.dp),
        focusRequester = focusRequester,
    ) {
        Text(
            text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun PosterImage(url: String, name: String, modifier: Modifier = Modifier, crop: Boolean = true) {
    Box(modifier.background(Color(0xFF222838)), contentAlignment = Alignment.Center) {
        if (url.startsWith("http")) {
            AsyncImage(
                model = url, contentDescription = null,
                contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(name.trim().take(2).uppercase(), color = Muted, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
fun LoadingBox(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Accent)
            Spacer(Modifier.height(8.dp))
            Text(text, color = Muted, fontSize = 12.sp)
            Text("Con listas grandes puede tardar un momento.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun EmptyBox(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

@Composable
fun StatusLine(model: AppModel) {
    if (model.message.isNotBlank()) {
        Text(
            model.message,
            color = if (model.messageIsError) ErrorColor else Muted,
            fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Botón que abre un menú desplegable de opciones. */
@Composable
fun Picker(label: String, options: List<String>, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Btn("$label  ▾", onClick = { open = true }, modifier = Modifier.fillMaxWidth())
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(
                    text = { Text(o, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { open = false; onPick(i) },
                )
            }
        }
    }
}

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%d:%02d", m, sec)
}
