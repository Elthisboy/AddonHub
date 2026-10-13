# Backend de AddonHub (proxy de CurseForge)

Un Cloudflare Worker pequeño que guarda la API key de CurseForge para que **no vaya dentro de la app**.
Solo reenvía las consultas que la app necesita, siempre de Minecraft Bedrock, y no guarda nada.

## Qué hace

| Ruta | Para qué |
|---|---|
| `GET /v1/mods/search` | Catálogo y buscador (solo parámetros permitidos, siempre Bedrock) |
| `GET /v1/categories` | Subcategorías (Terror, Magia…) |
| `GET /v1/mods/{id}/description` | Descripción de un contenido |
| `POST /v1/mods` | Datos de varios contenidos por id (dependencias, favoritos, descargas) |
| `POST /v1/mods/featured` | Populares y recientes (respaldo) |
| `GET /dl?u=<url>` | Descarga del CDN de CurseForge (solo `*.forgecdn.net` y solo `.mcpack/.mcaddon/.mcworld/.mctemplate`) |

Todo exige el encabezado `X-App-Token`. Nada se cachea (los términos de la API no lo permiten).

## Desplegar (una sola vez)

Necesitas una cuenta gratuita de Cloudflare y [Node.js](https://nodejs.org) instalado.

```bash
cd backend
npm install
npx wrangler login
npx wrangler secret put CF_API_KEY     # pega tu key de CurseForge cuando la pida
npx wrangler secret put APP_TOKEN      # inventa una cadena larga al azar (30+ caracteres)
npx wrangler deploy
```

`wrangler deploy` imprime la dirección, algo como `https://addonhub-proxy.TU-USUARIO.workers.dev`.

## Conectar la app

En `local.properties` (en la raíz del proyecto) agrega:

```properties
proxy.url=https://addonhub-proxy.TU-USUARIO.workers.dev
proxy.token=EL-MISMO-APP_TOKEN
```

Con `proxy.url` presente, la app **ya no incrusta la key**. Un release (`assembleRelease` / `bundleRelease`)
se niega a compilar si falta el proxy.

## Probar

```bash
npm test                                   # pruebas de la lógica (node --test)
```
```powershell
.\smoke-test.ps1 -Base https://addonhub-proxy.TU-USUARIO.workers.dev -Token TU_APP_TOKEN
```

La prueba de humo debe mostrar todo en ✅. Después, en la app: **⚙️ → Herramientas → Probar conexión**
(o simplemente abre Explorar y descarga algo pequeño).

## Límites que conviene conocer

- El `APP_TOKEN` va dentro del APK, así que un curioso podría extraerlo. Frena el uso casual, no a un
  atacante decidido. Para frenar abusos, en el panel de Cloudflare añade una regla de **Rate Limiting**
  (por ejemplo 120 peticiones por minuto por IP) sobre este Worker. Más adelante se puede añadir
  verificación con **Play Integrity**.
- Las descargas pasan por el Worker. El plan gratuito de Cloudflare tiene un tope diario de peticiones;
  si la app crece, hay que subir de plan.
- Cloudflare registra métricas técnicas (como cualquier servicio). El Worker no guarda contenido.
