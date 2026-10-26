package com.majano.iptv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
        val tv = detectTvMode(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) {
                IPTVApp(this, tv, player!!)
            }
        }
    }
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}
private fun detectTvMode(c: Context): Boolean {
    val ui = c.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    val mode = c.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    return ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION || mode == Configuration.UI_MODE_TYPE_TELEVISION
}
private enum class Section(val title: String) { LIVE("TV en vivo"), MOVIES("Películas"), SERIES("Series"), FAVORITES("Favoritos"), SETTINGS("Configuración") }
private data class MediaEntry(val id:String, val title:String, val group:String, val url:String, val kind:String, val logo:String="", val season:Int=0, val episode:Int=0)

@Composable
private fun IPTVApp(context: Context, detectedTv: Boolean, player: ExoPlayer) {
    val prefs = remember { context.getSharedPreferences("majano_settings", Context.MODE_PRIVATE) }
    var isTv by remember { mutableStateOf(prefs.getBoolean("tv_mode", detectedTv)) }
    var section by remember { mutableStateOf(Section.SETTINGS) }
    var server by remember { mutableStateOf(prefs.getString("server","") ?: "") }
    var username by remember { mutableStateOf(prefs.getString("username","") ?: "") }
    var password by remember { mutableStateOf(prefs.getString("password","") ?: "") }
    var m3u by remember { mutableStateOf(prefs.getString("m3u","") ?: "") }
    var status by remember { mutableStateOf("Introduce los datos de tu proveedor y pulsa Conectar.") }
    var connecting by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf(listOf<MediaEntry>()) }
    var selected by remember { mutableStateOf<MediaEntry?>(null) }
    var filter by remember { mutableStateOf("Todos") }
    var search by remember { mutableStateOf("") }
    val favIds = remember { mutableStateListOf<String>().apply { addAll(prefs.getStringSet("favorites", emptySet()) ?: emptySet()) } }
    val scope = rememberCoroutineScope()
    val wide = LocalConfiguration.current.screenWidthDp >= 700 || isTv
    fun play(item: MediaEntry) {
        if (item.url.isNotBlank()) {
            selected = item
            player.setMediaItem(MediaItem.fromUri(item.url)); player.prepare(); player.play()
        }
    }
    fun saveFav(id:String) {
        if (favIds.contains(id)) favIds.remove(id) else favIds.add(id)
        prefs.edit().putStringSet("favorites", favIds.toSet()).apply()
    }
    Column(Modifier.fillMaxSize().background(Bg)) {
        if (wide) {
            Row(Modifier.weight(1f).fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.width(180.dp).fillMaxHeight()) {
                    Text("MAJANO", color=Accent, fontSize=23.sp, fontWeight=FontWeight.Black)
                    Text("I P T V", color=MainText, letterSpacing=4.sp)
                    Spacer(Modifier.height(24.dp))
                    Section.values().forEach { s -> NavItem(s, section==s, true) { section=s } }
                    Spacer(Modifier.weight(1f))
                    Text("Modo", color=Muted)
                    Row { TextButton(onClick={isTv=false;prefs.edit().putBoolean("tv_mode",false).apply()}) { Text("Móvil") }; TextButton(onClick={isTv=true;prefs.edit().putBoolean("tv_mode",true).apply()}) { Text("TV") } }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Header(section, status, onRefresh={
                        if (server.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                            connecting=true; scope.launch { val result=loadXtream(server,username,password); entries=result.first; status=result.second; connecting=false }
                        } else status="Introduce los datos del proveedor en Configuración."
                    })
                    Spacer(Modifier.height(12.dp))
                    MainContent(section, entries, favIds, search, {search=it}, filter, {filter=it}, status, server, {server=it}, username, {username=it}, password, {password=it}, m3u, {m3u=it}, connecting, { 
                        if (connecting) return@MainContent
                        connecting=true; status="Conectando y cargando catálogo…"
                        scope.launch {
                            prefs.edit().putString("server",server.trim()).putString("username",username.trim()).putString("password",password).putString("m3u",m3u.trim()).apply()
                            val result = if (m3u.isNotBlank() && server.isBlank()) loadM3u(m3u.trim()) else loadXtream(server.trim(),username.trim(),password)
                            entries=result.first; status=result.second; connecting=false
                            if (entries.isNotEmpty()) section=Section.LIVE
                        }
                    }, { saveFav(it) }, { play(it) }, selected, player, wide)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal=12.dp, vertical=8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween, modifier=Modifier.fillMaxWidth()) {
                    Column { Text("MAJANO", color=Accent, fontSize=21.sp, fontWeight=FontWeight.Black); Text("I P T V", color=MainText, letterSpacing=3.sp, fontSize=10.sp) }
                    TextButton(onClick={
                        if (server.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                            connecting=true; scope.launch { val r=loadXtream(server,username,password); entries=r.first; status=r.second; connecting=false }
                        } else status="Introduce los datos en Configuración."
                    }) { Text("↻ Actualizar", color=Accent) }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    Section.values().forEach { s -> NavItem(s, section==s, false) { section=s } }
                }
                Spacer(Modifier.height(8.dp))
                Text(section.title, color=MainText, fontSize=22.sp, fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                MainContent(section, entries, favIds, search, {search=it}, filter, {filter=it}, status, server, {server=it}, username, {username=it}, password, {password=it}, m3u, {m3u=it}, connecting, {
                    if (connecting) return@MainContent
                    connecting=true; status="Conectando y cargando catálogo…"
                    scope.launch {
                        prefs.edit().putString("server",server.trim()).putString("username",username.trim()).putString("password",password).putString("m3u",m3u.trim()).apply()
                        val r=if(m3u.isNotBlank() && server.isBlank()) loadM3u(m3u.trim()) else loadXtream(server.trim(),username.trim(),password)
                        entries=r.first; status=r.second; connecting=false
                        if(entries.isNotEmpty()) section=Section.LIVE
                    }
                }, { saveFav(it) }, { play(it) }, selected, player, wide=false)
            }
        }
    }
}

@Composable private fun NavItem(s:Section, active:Boolean, wide:Boolean, onClick:()->Unit) {
    Surface(onClick=onClick, color=if(active) Color(0xFF29202A) else Color.Transparent, shape=RoundedCornerShape(10.dp),
        border=if(active) androidx.compose.foundation.BorderStroke(1.dp,Accent.copy(alpha=.7f)) else null) {
        Text(s.title, color=if(active) MainText else Muted, fontSize=if(wide) 14.sp else 12.sp,
            modifier=Modifier.padding(horizontal=if(wide)12.dp else 10.dp, vertical=if(wide)12.dp else 8.dp))
    }
}
@Composable private fun Header(section:Section,status:String,onRefresh:()->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically, modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) { Text(section.title,color=MainText,fontSize=28.sp,fontWeight=FontWeight.Bold); Text("Tu contenido, organizado en un solo lugar",color=Muted,fontSize=12.sp) }
        OutlinedButton(onClick=onRefresh) { Text("↻ Actualizar",color=Accent) }
    }
}
@Composable private fun MainContent(
    section:Section, entries:List<MediaEntry>, favorites:List<String>, search:String, onSearch:(String)->Unit,
    filter:String, onFilter:(String)->Unit, status:String, server:String, onServer:(String)->Unit, username:String, onUsername:(String)->Unit,
    password:String, onPassword:(String)->Unit, m3u:String, onM3u:(String)->Unit, connecting:Boolean, onConnect:()->Unit,
    onFavorite:(String)->Unit, onPlay:(MediaEntry)->Unit, selected:MediaEntry?, player:ExoPlayer, wide:Boolean
) {
    if(section==Section.SETTINGS) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(Panel,RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Conectar proveedor IPTV",color=MainText,fontSize=20.sp,fontWeight=FontWeight.Bold)
            Text("Introduce los datos de tu servicio. El catálogo se carga al conectar.",color=Muted,fontSize=13.sp)
            OutlinedTextField(server,onServer,label={Text("URL del servidor Xtream")},placeholder={Text("https://servidor:puerto")},singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri))
            OutlinedTextField(username,onUsername,label={Text("Usuario")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(password,onPassword,label={Text("Contraseña")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
            Text("Alternativa: usa M3U si tu proveedor no utiliza Xtream.",color=Muted,fontSize=12.sp)
            OutlinedTextField(m3u,onM3u,label={Text("URL de lista M3U")},singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri))
            Button(onClick=onConnect,enabled=!connecting,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) { Text(if(connecting) "Conectando…" else "Conectar y cargar catálogo") }
            Text(status,color=if(status.startsWith("Error")||status.contains("No se pudo")) Accent else Muted,fontSize=13.sp)
            Text("Los datos se guardan en este dispositivo. No los publiques en GitHub.",color=Muted,fontSize=11.sp)
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(search,onSearch,modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Buscar contenido")})
            val list = when(section) {
                Section.LIVE -> entries.filter { it.kind=="live" }
                Section.MOVIES -> entries.filter { it.kind=="movie" }
                Section.SERIES -> entries.filter { it.kind=="series" }
                Section.FAVORITES -> entries.filter { favorites.contains(it.id) }
                else -> emptyList()
            }.filter { it.title.contains(search,true) || it.group.contains(search,true) }
            if(wide) {
                Row(Modifier.fillMaxSize().padding(top=10.dp), horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    LazyColumn(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        if(list.isEmpty()) item { Text(if(entries.isEmpty()) "Conecta tu proveedor para cargar el catálogo." else "No hay contenido en esta categoría.",color=Muted) }
                        items(list.take(1500), key={it.id}) { item ->
                            Surface(onClick={onPlay(item)},color=if(selected?.id==item.id) Color(0xFF29202A) else Panel,shape=RoundedCornerShape(9.dp),modifier=Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(item.title,color=MainText); Text(item.group,color=Muted,fontSize=11.sp) }
                                    TextButton(onClick={onFavorite(item.id)}) { Text(if(favorites.contains(item.id)) "★" else "☆",color=Accent) }
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1.15f)) {
                        Text("Vista previa",color=MainText,fontWeight=FontWeight.SemiBold)
                        AndroidView(factory={ctx->PlayerView(ctx).apply{this.player=player;useController=true}},modifier=Modifier.fillMaxWidth().weight(1f).background(Color.Black))
                        Text(selected?.title ?: "Selecciona un canal o contenido",color=MainText)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement=Arrangement.spacedBy(8.dp), contentPadding=PaddingValues(vertical=8.dp)) {
                    if(list.isEmpty()) item { Text(if(entries.isEmpty()) "Conecta tu proveedor para cargar el catálogo." else "No hay contenido en esta categoría.",color=Muted) }
                    items(list.take(1500), key={it.id}) { item ->
                        Surface(color=Panel,shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(item.title,color=MainText,fontWeight=FontWeight.Medium); Text(item.group,color=Muted,fontSize=11.sp) }
                                TextButton(onClick={onPlay(item)}) { Text("Ver",color=Accent) }
                                TextButton(onClick={onFavorite(item.id)}) { Text(if(favorites.contains(item.id)) "★" else "☆",color=Accent) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun loadXtream(server:String,user:String,pass:String):Pair<List<MediaEntry>,String> = withContext(Dispatchers.IO) {
    if(server.isBlank()||user.isBlank()||pass.isBlank()) return@withContext emptyList<MediaEntry>() to "Error: introduce servidor, usuario y contraseña."
    try {
        val base=server.trim().trimEnd('/')
        if(!base.startsWith("http://")&&!base.startsWith("https://")) return@withContext emptyList<MediaEntry>() to "Error: la URL debe empezar por http:// o https://."
        val auth=fetchJson("$base/player_api.php?username=${Uri.encode(user)}&password=${Uri.encode(pass)}")
        if(auth.optJSONObject("user_info")?.optInt("auth",0)!=1) return@withContext emptyList<MediaEntry>() to "Error: el proveedor rechazó las credenciales."
        val out=mutableListOf<MediaEntry>()
        fun addArray(action:String,kind:String,extDefault:String) {
            val arr=fetchArray("$base/player_api.php?username=${Uri.encode(user)}&password=${Uri.encode(pass)}&action=$action")
            for(i in 0 until arr.length()) {
                val o=arr.optJSONObject(i) ?: continue
                val id=o.optString("stream_id",o.optString("series_id",o.optString("id","")))
                val title=o.optString("name",o.optString("title","Sin título"))
                val cat=o.optString("category_name",o.optString("category_id",if(kind=="live") "Canales" else if(kind=="movie") "Películas" else "Series"))
                val ext=o.optString("container_extension",extDefault)
                val path=when(kind) {
                    "live" -> "$base/live/${Uri.encode(user)}/${Uri.encode(pass)}/$id.${if(ext.isBlank()) "ts" else ext}"
                    "movie" -> "$base/movie/${Uri.encode(user)}/${Uri.encode(pass)}/$id.$ext"
                    else -> ""
                }
                out.add(MediaEntry("$kind-$id",title,cat,path,kind,o.optString("stream_icon")))
            }
        }
        addArray("get_live_streams","live","ts")
        addArray("get_vod_streams","movie","mp4")
        addArray("get_series","series","")
        if(out.isEmpty()) emptyList<MediaEntry>() to "Conexión correcta, pero el proveedor devolvió un catálogo vacío."
        else out to "Conectado. Se cargaron ${out.count{it.kind=="live"}} canales, ${out.count{it.kind=="movie"}} películas y ${out.count{it.kind=="series"}} series."
    } catch(e:Exception) { emptyList<MediaEntry>() to "Error al conectar: revisa la URL, conexión y datos del proveedor." }
}
private fun fetchJson(address:String):JSONObject {
    val c=URL(address).openConnection() as HttpURLConnection
    try { c.connectTimeout=12000;c.readTimeout=20000;c.setRequestProperty("Accept","application/json")
        if(c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
        return JSONObject(c.inputStream.bufferedReader().use{it.readText()})
    } finally { c.disconnect() }
}
private fun fetchArray(address:String):JSONArray {
    val c=URL(address).openConnection() as HttpURLConnection
    try { c.connectTimeout=15000;c.readTimeout=30000;c.setRequestProperty("Accept","application/json")
        if(c.responseCode !in 200..299) return JSONArray()
        return JSONArray(c.inputStream.bufferedReader().use{it.readText()})
    } finally { c.disconnect() }
}
private suspend fun loadM3u(url:String):Pair<List<MediaEntry>,String> = withContext(Dispatchers.IO) {
    try {
        if(!url.startsWith("http://")&&!url.startsWith("https://")) return@withContext emptyList<MediaEntry>() to "Error: la URL M3U debe empezar por http:// o https://."
        val c=URL(url).openConnection() as HttpURLConnection
        c.connectTimeout=12000;c.readTimeout=30000
        val lines=c.inputStream.bufferedReader().use{it.readLines()};c.disconnect()
        val out=mutableListOf<MediaEntry>(); var title="Canal"; var group="M3U"
        for(line in lines) {
            when {
                line.startsWith("#EXTINF",true) -> {
                    title=line.substringAfterLast(",","Canal").trim().ifBlank{"Canal"}
                    group=Regex("""group-title="([^"]+)"""").find(line)?.groupValues?.getOrNull(1) ?: "M3U"
                }
                line.startsWith("http://")||line.startsWith("https://") -> {
                    out.add(MediaEntry("m3u-${out.size}",title,group,line,"live")); title="Canal"
                }
            }
        }
        if(out.isEmpty()) emptyList<MediaEntry>() to "Error: no se encontraron enlaces en la lista M3U."
        else out to "Lista M3U cargada: ${out.size} elementos. La lista se muestra en TV en vivo."
    } catch(e:Exception) { emptyList<MediaEntry>() to "Error al cargar M3U. Revisa la dirección y el acceso." }
}
