@file:OptIn(ExperimentalMaterial3Api::class)

package com.majano.iptv

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** TV en vivo: categorías | canales | vista previa (en teléfono: reproductor arriba + lista). */
@Composable
fun LiveScreen(model: AppModel, wide: Boolean) {
    LaunchedEffect(Unit) { model.loadKind(Kind.LIVE) }
    var query by remember { mutableStateOf("") }
    var catId by remember { mutableStateOf<String?>(null) }
    val all = model.items[Kind.LIVE].orEmpty()
    val cats = model.cats[Kind.LIVE].orEmpty()
    val loading = model.isLoading(Kind.LIVE)
    val counts = remember(all) { all.groupingBy { it.categoryId }.eachCount() }
    val visibleCats = remember(cats, counts) { cats.filter { (counts[it.id] ?: 0) > 0 } }
    val filtered = remember(all, catId, query) {
        val q = query.trim()
        all.filter { (catId == null || it.categoryId == catId) && (q.isEmpty() || it.name.contains(q, ignoreCase = true)) }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            CategoryColumn(visibleCats, counts, all.size, catId, { catId = it }, Modifier.width(190.dp).fillMaxHeight())
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("TV en vivo", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${filtered.size} canales", color = Muted, fontSize = 12.sp)
                    }
                    Btn("↻", { model.loadKind(Kind.LIVE, force = true) })
                }
                Spacer(Modifier.height(6.dp))
                SearchField(query, "Buscar canal") { query = it }
                Spacer(Modifier.height(6.dp))
                ChannelList(model, filtered, loading, Modifier.weight(1f).fillMaxWidth())
                StatusLine(model)
            }
            Spacer(Modifier.width(10.dp))
            PreviewPane(model, Modifier.weight(1.25f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            if (model.hasMedia) PlayerPhone(model)
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TV en vivo", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${filtered.size} canales", color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                SearchField(query, "Buscar canal") { query = it }
                if (visibleCats.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    val label = visibleCats.firstOrNull { it.id == catId }?.name ?: "Todas las categorías"
                    Picker(label, listOf("Todas las categorías") + visibleCats.map { it.name }) { i ->
                        catId = if (i == 0) null else visibleCats[i - 1].id
                    }
                }
                Spacer(Modifier.height(6.dp))
                ChannelList(model, filtered, loading, Modifier.weight(1f).fillMaxWidth())
                StatusLine(model)
            }
        }
    }
}

@Composable
fun SearchField(value: String, label: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
    )
}

@Composable
private fun CategoryColumn(
    cats: List<Category>,
    counts: Map<String, Int>,
    total: Int,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Text("Categorías", color = MainText, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item { CatRow("Todas", total, selected == null) { onSelect(null) } }
            items(cats) { c -> CatRow(c.name, counts[c.id] ?: 0, selected == c.id) { onSelect(c.id) } }
        }
    }
}

@Composable
private fun CatRow(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Focusable(onClick = onClick, selected = selected, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                name, color = if (selected) MainText else SoftText, fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ChannelList(model: AppModel, list: List<Entry>, loading: Boolean, modifier: Modifier) {
    Box(modifier) {
        when {
            loading && list.isEmpty() -> LoadingBox("Cargando canales del proveedor…")
            list.isEmpty() -> EmptyBox("No hay canales para mostrar.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(list) { i, e ->
                    ChannelRow(model, e, i + 1, model.hasMedia && model.current === e) { model.open(e, list) }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(model: AppModel, e: Entry, number: Int, playing: Boolean, onClick: () -> Unit) {
    Focusable(onClick = onClick, selected = playing, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(number.toString(), color = if (playing) Accent else Muted, fontSize = 12.sp, modifier = Modifier.width(32.dp))
            PosterImage(e.image, e.name, Modifier.size(56.dp, 40.dp).clip(RoundedCornerShape(6.dp)), crop = false)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, color = MainText, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                if (playing) Text("Reproduciendo", color = Accent, fontSize = 11.sp)
            }
            Text(
                if (model.isFav(e)) "★" else "☆", color = Accent, fontSize = 22.sp,
                modifier = Modifier.padding(horizontal = 6.dp).pointerInput(e) { detectTapGestures { model.toggleFav(e) } },
            )
        }
    }
}

@Composable
private fun PreviewPane(model: AppModel, modifier: Modifier) {
    val cur = model.current
    Column(modifier) {
        PlayerBox(model, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.height(10.dp))
        Text(
            model.nowTitle.ifBlank { "Elige un canal" }, color = MainText, fontSize = 16.sp,
            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (model.hasMedia) Text("EN VIVO", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (model.hasMedia) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Pantalla completa", { model.enterFullscreen() }, Modifier.weight(1.4f), primary = true)
                if (cur != null) Btn(if (model.isFav(cur)) "★ Favorito" else "☆ Favorito", { model.toggleFav(cur) }, Modifier.weight(1f))
                Btn("Audio / Subt.", { model.tracksOpen = true }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text("Toca o presiona OK otra vez sobre el canal para verlo en pantalla completa.", color = Muted, fontSize = 11.sp)
        }
    }
}
