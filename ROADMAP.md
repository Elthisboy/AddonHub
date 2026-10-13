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
- ✅ Modo adulto escondido (lo sexual siempre bloqueado; se apaga al salir de la app)
- ✅ Backend (proxy) escrito y verificado: 27 pruebas con `node --test` + comprobación en vivo contra CurseForge
  (categorías, búsqueda, lote, descripción, descarga real de un .mcpack, `Range`)
- ✅ El release no lleva la key (verificado en el APK)
- 🙋 Desplegar el proxy (`backend/README.md`) y probarlo con `smoke-test.ps1`
- ✅ Botón "Ver en CurseForge" en el detalle (pide PIN; solo abre enlaces de curseforge.com, con pruebas)
- ✅ Repositorio git preparado (commit local sin secretos), README, sitio y política de privacidad en inglés
- 🙋 Crear el repo `Elthisboy/AddonHub` en GitHub, hacer `git push` y activar Pages (`/docs`)
- 🙋 Enviar el formulario de la key de tercero (respuestas en `docs/formulario-curseforge-respuestas.md`)
- 🙋 Poner fecha y contacto en la política de privacidad y revisarla

## Identidad y pulido
- ✅ Icono adaptable (con versión monocromática) y mascota "Hubi" (rebota; cambia de ánimo al descargar)
- ✅ Tipografía pixelada Silkscreen (licencia SIL OFL en `docs/licencias/`), solo en títulos y botones
- ✅ Tema propio verde (antes los botones salían morados)
- ✅ Revisión visual en 320 dp, 360 dp y tableta con capturas automáticas (`UiScreenshotsTest`, salen en
  `app/build/screenshots/`); corrigió desbordes de la fuente pixelada y pestañas que no cabían
- ⬜ Textos en inglés: los textos están dentro del código y de las capas de lógica (filtro, errores,
  avisos); traducir exige moverlos a recursos (~250 textos). Decisión pendiente.
- ⬜ Limitar el ancho del contenido en tabletas (hoy se estira a toda la pantalla)
- ⬜ Sonidos (necesitan archivos de audio con licencia)
- ⬜ Probar en dispositivos reales con Android 8 a 15

## Publicación
- ✅ Release con R8 que compila (1,5 MB) y se niega a compilar sin proxy
- 🙋 Crear la llave de firma y `keystore.properties` (`docs/publicacion-play.md`)
- 🙋 Cuenta de Play Console, ficha, Families, pruebas cerradas
- 🟡 Reanudar descargas cortadas (hasta 4 intentos con `Range`; 11 pruebas con servidor local)
- ⬜ Verificación de la app con Play Integrity (frena abusos del proxy)
