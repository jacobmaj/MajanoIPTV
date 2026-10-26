# Majano IPTV — versión pulida (base funcional)

Incluye:
- Icono Android y flujo GitHub Actions.
- Interfaz adaptable: navegación lateral para pantallas amplias y navegación horizontal para móvil.
- Configuración desplazable, botón Conectar y campos Xtream/M3U.
- Validación Xtream y carga inicial de canales, películas y series desde la API del proveedor.
- Lectura de listas M3U como lista de reproducción.
- Búsqueda en el catálogo y favoritos guardados localmente.
- Reproductor Media3 para URLs reproducibles por el dispositivo.

## Limitaciones que siguen pendientes
- Los episodios y temporadas de series todavía necesitan una pantalla de detalle y llamadas a `get_series_info`; el listado de series se carga, pero no se puede reproducir una serie desde su fila.
- Los favoritos se guardan localmente; la contraseña de proveedor se guarda en preferencias privadas de la app, no cifrada. Antes de distribuir públicamente, debe migrarse a almacenamiento cifrado.
- El formato, codecs, 4K, subtítulos y pistas de audio dependen del stream, proveedor y dispositivo.
- La lista M3U se presenta como lista de TV en vivo; clasificación avanzada de películas/series por M3U no está implementada.
- No se ha podido ejecutar una compilación Android real en este entorno. Verifica el resultado en GitHub Actions y comparte el error si falla.

## GitHub
Sube el contenido del ZIP a tu repositorio existente, conservando `.github/workflows/build-apk.yml`. Ejecuta Actions y descarga el artefacto `majano-iptv-debug-apk`.
