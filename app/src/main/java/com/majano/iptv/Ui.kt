@file:OptIn(ExperimentalMaterial3Api::class)

package com.majano.iptv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Section(val title: String, val short: String) {
    LIVE("TV en vivo", "TV"),
    MOVIES("Películas", "Películas"),
    SERIES("Series", "Series"),
    HISTORY("Continuar viendo", "Historial"),
    FAVORITES("Favoritos", "Favoritos"),
    ACCOUNT("Cuenta", "Cuenta"),
}

@Composable
fun IPTVRoot(model: AppModel, onFullscreen: (Boolean) -> Unit) {
    LaunchedEffect(model.fullscreen) { onFullscreen(model.fullscreen) }
    BackHandler(enabled = model.fullscreen) { model.exitFullscreen() }
    if (model.loggedIn) MainScreen(model) else LoginScreen(model)
    if (model.tracksOpen) TrackDialog(model.player) { model.tracksOpen = false }
}

// ───────────────────────── Login ─────────────────────────

@Composable
fun LoginScreen(model: AppModel) {
    var link by remember { mutableStateOf(model.savedLink) }
    var user by remember { mutableStateOf(model.savedUser) }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    val canSubmit = !model.busy && link.isNotBlank()
    val submit = { if (canSubmit) model.login(link, user, pass) }

    Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("MAJANO", color = Accent, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("IPTV", color = MainText, fontSize = 14.sp, letterSpacing = 6.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = link, onValueChange = { link = it },
                label = { Text("Link del servidor") },
                placeholder = { Text("http://servidor.com:8080") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = user, onValueChange = { user = it },
                label = { Text("Usuario") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = pass, onValueChange = { pass = it },
                label = { Text("Contraseña") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showPass = !showPass }) { Text(if (showPass) "Ocultar" else "Ver", color = Muted, fontSize = 12.sp) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Button(onClick = { submit() }, enabled = canSubmit, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (model.busy) "Conectando…" else "Entrar", fontSize = 16.sp)
            }
            if (model.busy) CircularProgressIndicator(color = Accent, modifier = Modifier.size(28.dp))
            if (model.message.isNotBlank()) {
                Text(model.message, color = if (model.messageIsError) ErrorColor else Muted, fontSize = 13.sp)
            }
            Text("Usa únicamente datos de un servicio al que tengas acceso.", color = Muted, fontSize = 11.sp)
        }
    }
}

// ───────────────────────── Pantalla principal ─────────────────────────

@Composable
fun MainScreen(model: AppModel) {
    var section by remember { mutableStateOf(Section.LIVE) }
    val select: (Section) -> Unit = { s ->
        model.closeDetail()
        // Al salir de TV en vivo se detiene la vista previa (si no, seguiría sonando sin verse).
        if (s != Section.LIVE && !model.fullscreen && model.isLive) model.stopPlayback()
        section = s
    }
    BackHandler(enabled = model.detail != null && !model.fullscreen) { model.closeDetail() }

    if (model.fullscreen && model.hasMedia) {
        FullPlayer(model)
    } else {
        BoxWithConstraints(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
            val wide = maxWidth >= 720.dp
            if (wide) {
                Row(Modifier.fillMaxSize().padding(12.dp)) {
                    SideNav(section, select)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f).fillMaxHeight()) { SectionContent(model, section, true) }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { SectionContent(model, section, false) }
                    BottomNav(section, select)
                }
            }
        }
    }
}

@Composable
private fun SectionContent(model: AppModel, section: Section, wide: Boolean) {
    if (model.detail != null) {
        DetailScreen(model, wide)
        return
    }
    val pad = if (wide || section == Section.LIVE) Modifier else Modifier.padding(12.dp)
    Box(pad.fillMaxSize()) {
        when (section) {
            Section.LIVE -> LiveScreen(model, wide)
            Section.MOVIES -> MediaHome(model, Kind.MOVIE, wide)
            Section.SERIES -> MediaHome(model, Kind.SERIES, wide)
            Section.HISTORY -> HistoryScreen(model)
            Section.FAVORITES -> FavoritesScreen(model)
            Section.ACCOUNT -> AccountPanel(model, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SideNav(current: Section, onSelect: (Section) -> Unit) {
    Column(Modifier.width(140.dp).fillMaxHeight()) {
        Text("MAJANO", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text("IPTV", color = MainText, fontSize = 12.sp, letterSpacing = 4.sp)
        Spacer(Modifier.height(20.dp))
        Section.values().forEach { s ->
            Focusable(onClick = { onSelect(s) }, selected = s == current, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    s.title, color = if (s == current) MainText else Muted, fontSize = 14.sp,
                    fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun BottomNav(current: Section, onSelect: (Section) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 4.dp, vertical = 4.dp)) {
        Section.values().forEach { s ->
            Focusable(onClick = { onSelect(s) }, selected = s == current, modifier = Modifier.weight(1f).padding(2.dp)) {
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(
                        s.short, color = if (s == current) MainText else Muted, fontSize = 11.sp, maxLines = 1,
                        fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountPanel(model: AppModel, modifier: Modifier) {
    Column(modifier.background(Panel, RoundedCornerShape(16.dp)).padding(18.dp).verticalScroll(rememberScrollState())) {
        Text("Cuenta", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(model.accountInfo.ifBlank { "Conectado" }, color = Muted, fontSize = 13.sp)
        Text("Servidor: ${model.session?.base.orEmpty()}", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Text("Saltar intro: avanza este tiempo al tocar \"Saltar intro\" en un episodio.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        val opts = listOf(45, 60, 75, 85, 90, 120)
        Picker("Salto de intro: ${model.introSkipSec} s", opts.map { "$it segundos" }, Modifier.width(240.dp)) { i ->
            model.setIntroSkip(opts[i])
        }
        Spacer(Modifier.height(16.dp))
        Btn("Cerrar sesión", { model.logout() }, primary = true)
    }
}
