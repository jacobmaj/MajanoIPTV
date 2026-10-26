package com.majano.iptv

import android.app.UiModeManager
import android.net.Uri
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView

private val Bg = Color(0xFF090B10)
private val Panel = Color(0xFF151923)
private val Accent = Color(0xFFE50914)
private val MainText = Color(0xFFF5F6F8)
private val Muted = Color(0xFF9CA3AF)

class MainActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        val tvMode = detectTvMode(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) {
                IPTVApp(isTvInitially = tvMode, player = player!!)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Let Android/system handle volume. Channel +/- keys are handled by the UI only when delivered to this app.
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.onKeyDown(keyCode, event)
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}

private fun detectTvMode(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION || mode == Configuration.UI_MODE_TYPE_TELEVISION
}

private enum class Section(val title: String) { LIVE("TV en vivo"), MOVIES("Películas"), SERIES("Series"), FAVORITES("Favoritos"), SETTINGS("Configuración") }
private data class DemoItem(val title: String, val group: String, val url: String = "")

@Composable
private fun IPTVApp(isTvInitially: Boolean, player: ExoPlayer) {
    var isTv by remember { mutableStateOf(isTvInitially) }
    var section by remember { mutableStateOf(Section.LIVE) }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var playlistUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Agrega los datos de tu proveedor para cargar tu catálogo.") }
    val scope = rememberCoroutineScope()
    var connecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("Configura tu servicio") }
    var testStream by remember { mutableStateOf("") }
    val favorites = remember { mutableStateListOf<String>() }
    val sample = remember { listOf(
        DemoItem("Canales de ejemplo", "Información", ""),
        DemoItem("Configura tu servicio IPTV", "Configuración", "")
    ) }

    Row(Modifier.fillMaxSize().background(Bg).padding(if (isTv) 24.dp else 12.dp)) {
        Column(Modifier.width(if (isTv) 210.dp else 108.dp).fillMaxHeight()) {
            Text("MAJANO", color = Accent, fontSize = if (isTv) 23.sp else 15.sp, fontWeight = FontWeight.Black)
            Text("IPTV", color = MainText, fontSize = if (isTv) 13.sp else 10.sp, letterSpacing = 4.sp)
            Spacer(Modifier.height(24.dp))
            Section.values().forEach { item ->
                NavigationTile(item.title, section == item, isTv) { section = item }
            }
            Spacer(Modifier.weight(1f))
            Text("Modo", color = Muted, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { isTv = false }) { Text("Móvil", color = if (!isTv) Accent else Muted, fontSize = 11.sp) }
                TextButton(onClick = { isTv = true }) { Text("TV", color = if (isTv) Accent else Muted, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(section.title, color = MainText, fontSize = if (isTv) 28.sp else 21.sp, fontWeight = FontWeight.Bold)
                    Text("Tu contenido, organizado en un solo lugar", color = Muted, fontSize = 12.sp)
                }
                OutlinedButton(onClick = { status = "Actualización solicitada. Conecta un proveedor para sincronizar el catálogo." }) { Text("↻ Actualizar") }
            }
            Spacer(Modifier.height(16.dp))
            when (section) {
                Section.SETTINGS -> SettingsPanel(
                    isTv, server, { server = it }, username, { username = it }, password, { password = it },
                    playlistUrl, { playlistUrl = it }, status,
                    onSave = {
                        if (connecting) { /* Evitar solicitudes duplicadas */ }
                        else if (playlistUrl.isNotBlank() && server.isBlank()) {
                            status = "La validación de listas M3U está pendiente. Para probar la conexión usa Xtream."
                        } else if (server.isBlank() || username.isBlank() || password.isBlank()) {
                            status = "Introduce servidor, usuario y contraseña de Xtream."
                        } else {
                            connecting = true
                            status = "Comprobando conexión con el proveedor…"
                            scope.launch {
                                status = validateXtream(server.trim(), username.trim(), password)
                                connecting = false
                            }
                        }
                    },
                    onTest = { url ->
                        testStream = url
                        if (url.startsWith("https://") || url.startsWith("http://")) {
                            player.setMediaItem(MediaItem.fromUri(url)); player.prepare(); player.play()
                            selected = "Prueba de reproducción"
                            status = "Se envió la URL al reproductor. Si no reproduce, verifica formato, acceso y compatibilidad del stream."
                        } else status = "Para probar un stream, introduce una URL http(s) válida."
                    }
                )
                else -> {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(0.85f).fillMaxHeight()) {
                            Text(if (section == Section.LIVE) "Grupos y canales" else if (section == Section.MOVIES) "Catálogo de películas" else if (section == Section.SERIES) "Series y temporadas" else "Tus favoritos", color = MainText, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            if (section == Section.SERIES) {
                                var season by remember { mutableStateOf("Temporada 1") }
                                var expanded by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(onClick = { expanded = true }) { Text("$season  ▾") }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        listOf("Temporada 1", "Temporada 2", "Temporada 3").forEach { value ->
                                            DropdownMenuItem(text = { Text(value) }, onClick = { season = value; expanded = false })
                                        }
                                    }
                                }
                                Text("Los capítulos aparecerán aquí cuando se conecte el catálogo.", color = Muted, fontSize = 12.sp)
                            }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(sample) { item ->
                                    Surface(color = Panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(item.title, color = MainText, fontWeight = FontWeight.Medium)
                                            Text(item.group, color = Muted, fontSize = 11.sp)
                                            Row {
                                                TextButton(onClick = { selected = item.title }) { Text("Ver", color = Accent) }
                                                TextButton(onClick = { if (favorites.contains(item.title)) favorites.remove(item.title) else favorites.add(item.title) }) {
                                                    Text(if (favorites.contains(item.title)) "★ Favorito" else "☆ Favorito", color = MainText)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Column(Modifier.weight(1.15f).fillMaxHeight()) {
                            Text("Vista previa", color = MainText, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Surface(color = Color.Black, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                                Box(contentAlignment = Alignment.Center) {
                                    AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
                                    if (player.mediaItemCount == 0) Text("Selecciona un contenido\no configura tu servicio", color = Muted, fontSize = 13.sp)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(selected, color = MainText, fontWeight = FontWeight.SemiBold)
                            Text(status, color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationTile(title: String, active: Boolean, isTv: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (active) Color(0xFF29202A) else Color.Transparent,
        shape = RoundedCornerShape(10.dp), border = if (active) BorderStroke(1.dp, Accent.copy(alpha = 0.7f)) else null,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(title, color = if (active) MainText else Muted, fontSize = if (isTv) 14.sp else 10.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = if (isTv) 13.dp else 9.dp), fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun SettingsPanel(
    isTv: Boolean, server: String, onServer: (String) -> Unit, username: String, onUsername: (String) -> Unit,
    password: String, onPassword: (String) -> Unit, playlistUrl: String, onPlaylistUrl: (String) -> Unit,
    status: String, onSave: () -> Unit, onTest: (String) -> Unit
) {
    var sampleStream by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Panel, RoundedCornerShape(16.dp)).verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Conectar proveedor IPTV", color = MainText, fontSize = if (isTv) 22.sp else 17.sp, fontWeight = FontWeight.Bold)
        Text("Usa únicamente los datos de un servicio al que tengas acceso.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(value = server, onValueChange = onServer, label = { Text("URL del servidor Xtream") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = username, onValueChange = onUsername, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = onPassword, label = { Text("Contraseña") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = playlistUrl, onValueChange = onPlaylistUrl, label = { Text("URL de lista M3U (alternativa)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Conectar proveedor") }
        }
        Divider(Modifier.padding(vertical = 12.dp), color = Muted.copy(alpha = 0.25f))
        Text("Prueba de reproducción (desarrollo)", color = MainText, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(value = sampleStream, onValueChange = { sampleStream = it }, label = { Text("URL directa HLS/MP4") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onTest(sampleStream) }) { Text("Probar stream") }
        Spacer(Modifier.height(8.dp))
        Text(status, color = Muted, fontSize = 12.sp)
        Text("Pendiente para la siguiente fase: autenticación Xtream/M3U completa, carga de categorías, persistencia cifrada, favoritos persistentes, búsqueda, temporadas/capítulos reales y selector de audio/subtítulos.", color = Muted, fontSize = 11.sp)
    }
}


// Valida credenciales Xtream sin registrar ni mostrar la contraseña.
// Esta comprobación no sincroniza todavía canales, películas ni series.
private suspend fun validateXtream(server: String, username: String, password: String): String = withContext(Dispatchers.IO) {
    try {
        val base = server.trimEnd('/')
        if (!base.startsWith("https://") && !base.startsWith("http://")) {
            return@withContext "La URL debe comenzar por https:// o http://."
        }
        val address = "$base/player_api.php?username=${Uri.encode(username)}&password=${Uri.encode(password)}"
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 12000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            if (connection.responseCode != 200) return@withContext "El servidor respondió HTTP ${connection.responseCode}. Revisa la dirección y el servicio."
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val auth = JSONObject(response).optJSONObject("user_info")?.optInt("auth", 0) ?: 0
            if (auth == 1) "Conexión Xtream verificada. La descarga del catálogo aún está pendiente."
            else "El servidor no autorizó las credenciales."
        } finally { connection.disconnect() }
    } catch (e: Exception) {
        "No se pudo conectar. Comprueba la URL, la red y el certificado HTTPS."
    }
}
