# Borrador para el formulario de key de CurseForge

Formulario: <https://forms.monday.com/forms/dce5ccb7afda9a1c21dab1a1aa1d84eb?r=use1>
Debes aceptar los [Términos para desarrolladores de terceros](https://support.curseforge.com/en/support/solutions/articles/9000207405-curse-forge-3rd-party-api-terms-and-conditions).
Overwolf dice que revisa el efecto en las ganancias de los autores, la carga en su CDN y si los autores
consienten la distribución por terceros ([artículo](https://support.curseforge.com/support/solutions/articles/9000208346-about-the-curseforge-api-and-how-to-apply-for-a-key)).
Este texto responde a eso. Adáptalo con tus datos reales y no afirmes nada que no sea cierto.

---

**Nombre del proyecto:** AddonHub

**Contacto:** [TU NOMBRE] · [TU CORREO]

**Qué es:** Una app Android gratuita, pensada para niños y niñas de 6 a 15 años, que facilita instalar
contenido de Minecraft Bedrock (addons, mapas, texturas y skins) que ya está publicado en CurseForge. La app
muestra el contenido con el nombre del autor y descarga el archivo oficial desde el CDN de CurseForge para
abrirlo con Minecraft. No aloja ni redistribuye archivos propios.

**Cómo usamos la API (Minecraft Bedrock, gameId 78022):**
- Buscar y listar contenido (`mods/search`) y sus categorías (`categories`).
- Leer la descripción y las dependencias (`mods/{id}/description`, `mods`).
- Descargar el archivo más reciente cuando el autor permite la distribución (`allowModDistribution`)
  y el archivo tiene enlace de descarga directo.

**Respeto a los autores:**
- Solo mostramos e instalamos contenido cuyo autor permite la distribución.
- Siempre mostramos el nombre del autor, y las dependencias que el autor declara.
- Cada descarga sale del CDN de CurseForge, por lo que cuenta como descarga normal del archivo.

**Monetización:** [ELIGE UNA Y SÉ EXACTO]
- Sin anuncios y sin cobrar por contenido. Opcionalmente, un apoyo voluntario de pago único que no
  desbloquea nada, para cubrir los costos del servidor y de la cuenta de desarrollador.

**Cumplimiento técnico:**
- La API key está solo en nuestro servidor intermediario; no se distribuye dentro de la app.
- No guardamos ni cacheamos datos de la API: los favoritos y el historial guardan solo ids y se piden
  en vivo.
- Volumen esperado: [ESTIMA TUS USUARIOS Y CONSULTAS POR USUARIO AL DÍA].

**Seguridad infantil:** perfiles por edad con filtro de contenido, control de padres con PIN, sin cuentas,
sin anuncios y sin recopilar datos personales. Aviso visible de que es una app no oficial.
