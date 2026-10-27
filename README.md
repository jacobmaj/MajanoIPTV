# Majano IPTV (v0.4.0)

Reproductor IPTV para Android / Android TV (Kotlin + Jetpack Compose + Media3).
Escribes **link del servidor, usuario y contraseña** y la app carga el catálogo de tu proveedor.

## Qué incluye
- **Login simple**: acepta `http://servidor:puerto`, o el link completo `get.php?username=...`. Xtream Codes con alternativa M3U. Errores claros (contraseña, cuenta vencida, servidor, certificado...).
- **Sesión guardada** cifrada (Android Keystore) y entrada automática.
- **TV en vivo** (categorías | canales | vista previa), **Películas** y **Series** (banner + filas de pósters), detalle con sinopsis, temporadas y episodios.
- **Continuar viendo**: recuerda película, temporada, episodio y minuto. Al volver a una serie queda en la temporada donde ibas.
- **Siguiente episodio** automático y sugerencia en los créditos; **Saltar intro** (ajustable en Cuenta).
- **Audio y subtítulos**: botón CC para desactivar subtítulos y cambiar de audio. Decodificadores FFmpeg para AC3/EAC3/DTS.
- **Control remoto**: OK = pausa, ◀ ▶ = ±10 s, ▲ ▼ = canal, ▼ = audio/subtítulos, Atrás = salir. CH+/CH- cambian de canal.
- Favoritos, búsqueda y pantalla completa.

## Compilar
GitHub Actions (`.github/workflows/build-apk.yml`) genera el APK de depuración: pestaña **Actions** → última ejecución → **Artifacts**.
Requiere Gradle 8.11.1 y Java 17 (ya configurados en el workflow).

## Notas
- Se permite tráfico `http` porque casi todos los proveedores IPTV no usan https.
- Los subtítulos quemados en la imagen del video no se pueden quitar.
- Usa únicamente datos de un servicio al que tengas acceso.
