# Roadmap de AddonHub

Leyenda: ✅ hecho y verificado · 🟡 hecho, falta probar en teléfono · ⬜ pendiente · 🙋 lo tienes que hacer tú

## Núcleo
- ✅ Descargar e importar a Minecraft (probado en teléfono)
- ✅ Descargas en segundo plano con notificación (probado en teléfono)
- 🟡 Varios archivos seguidos (dependencias) con "Importar siguiente"

## Catálogo
- ✅ Catálogo, categorías y subcategorías, orden, "Ver más" (50 por página)
- 🟡 Buscador por contenido → creador → palabras clave
- 🟡 Detalle con capturas, resumen, descripción, dependencias y requisitos del autor
- 🟡 Favoritos y "Mis descargas" (solo ids, datos en vivo)

## Seguridad y legal
- ✅ Modo padres: PIN, perfiles por edad, palabras bloqueadas, permitidos, herramientas ocultas
- ✅ Filtro de contenido (tests + medición con datos reales)
- ✅ Perfil "15 años o más": todo menos lo sexual, sin modo adulto
- ✅ Modo adulto escondido (lo sexual siempre bloqueado; se apaga al salir de la app)
- ✅ Backend (proxy) escrito y verificado: 27 pruebas con `node --test` + comprobación en vivo contra CurseForge
  (categorías, búsqueda, lote, descripción, descarga real de un .mcpack, `Range`)
- ✅ El release no lleva la key (verificado en el APK)
- ✅ Key de tercero aprobada por CurseForge (14 oct 2026)
- ✅ Proxy desplegado en Cloudflare y `smoke-test.ps1` en verde; la app ya lo usa (debug y release)
- ✅ Botón "Ver en CurseForge" en el detalle (pide PIN; solo abre enlaces de curseforge.com, con pruebas)
- ✅ Repositorio git preparado (commit local sin secretos), README, sitio y política de privacidad en inglés
- 🙋 Crear el repo `Elthisboy/AddonHub` en GitHub, hacer `git push` y activar Pages (`/docs`)
- 🙋 Poner fecha y contacto en la política de privacidad y revisarla

## Identidad y pulido
- ✅ Icono adaptable (con versión monocromática) y mascota "Hubi" (rebota; cambia de ánimo al descargar)
- ✅ Tipografía pixelada Silkscreen (licencia SIL OFL en `docs/licencias/`), solo en títulos y botones
- ✅ Tema propio verde (antes los botones salían morados)
- ✅ Revisión visual en 320 dp, 360 dp y tableta con capturas automáticas (`UiScreenshotsTest`, salen en
  `app/build/screenshots/`); corrigió desbordes de la fuente pixelada y pestañas que no cabían
- ✅ Idiomas: la app toma el idioma del teléfono (español o inglés) y, si no es ninguno de los dos, usa inglés.
  Los ~220 textos viven en `strings.xml` (`values/` inglés, `values-es/` español); la lógica (errores, avisos,
  filtro) devuelve `UiText`, que se traduce solo en la pantalla. Pruebas: mismas claves y argumentos en ambos
  archivos, español aplicado, francés/japonés caen en inglés. Android 13+ también la muestra en
  Ajustes → Idioma de la app.
- ⬜ Más idiomas (portugués, francés…): basta añadir `values-xx/strings.xml` y la lista en `locales_config.xml`.
  El filtro de contenido solo conoce palabras en inglés y español.
- ✅ Ancho máximo de 640 dp en tabletas (columna centrada)
- ✅ Accesibilidad: nombres para TalkBack en iconos y pestañas, zonas táctiles de 48 dp (4 pruebas)
- ✅ Icono cambiado a la mascota Hubi: el bloque de césped anterior se parecía demasiado al de Minecraft
- ✅ Material de Google Play en `store/` (icono 512 común; imagen destacada 1024×500 y 4 capturas 1080×2160
  en `store/es/` y `store/en/`) con contenido ficticio; se regenera con
  `gradlew testDebugUnitTest --tests "*Store*"`
- ⬜ Sonidos (necesitan archivos de audio con licencia)
- ⬜ Probar en dispositivos reales con Android 8 a 15

## Publicación
- ✅ Release con R8 que compila (1,5 MB) y se niega a compilar sin proxy
- ✅ Llave de subida `addonhub-upload.jks` + `keystore.properties` (fuera de git; 🙋 guarda una copia de respaldo)
- ✅ App creada en Play Console (`com.elthisboy.addonhub`, target API 36) y 0.11.0 publicada en **prueba interna**
- 🙋 Ficha de la tienda, contenido de la app (Families, seguridad de datos, clasificación) y prueba cerrada
- 🟡 Reanudar descargas cortadas (hasta 4 intentos con `Range`; 11 pruebas con servidor local)
- ⬜ Verificación de la app con Play Integrity (frena abusos del proxy)
