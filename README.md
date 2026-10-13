# AddonHub

A free, kid-friendly Android app that installs **Minecraft Bedrock** content (add-ons, maps, texture packs and
skins) from [CurseForge](https://www.curseforge.com/minecraft-bedrock) in a few taps.

> **Unofficial.** Not affiliated with, endorsed by, or associated with Mojang, Microsoft, or Overwolf / CurseForge.
> "Minecraft" is a trademark of Mojang Synergies AB.

- Website and privacy policy: <https://elthisboy.github.io/AddonHub/>
- Status and pending work: [ROADMAP.md](ROADMAP.md)

## How it works

The app lists content from the CurseForge API, downloads the file from CurseForge's CDN and hands it to
Minecraft with an Android intent, which imports it. It never hosts or re-uploads files, only shows content
whose author allows distribution, credits the author, and respects the dependencies the author declares.

- **Kotlin + Jetpack Compose**, OkHttp, Coil. Min SDK 26.
- **Parental controls:** age profiles, PIN, content filter, parent-approved list.
- **Background downloads** with a foreground service, resumable with HTTP `Range`.
- **No ads, no accounts, no analytics.**

## Layout

| Path | What |
|---|---|
| `app/` | The Android app |
| `backend/` | Cloudflare Worker proxy that keeps the CurseForge API key off the device ([README](backend/README.md)) |
| `docs/` | Website (GitHub Pages), privacy policy and publishing notes |

## Build

Requirements: Android Studio (or JDK 17 and the Android SDK).

```bash
./gradlew testDebugUnitTest assembleDebug
```

For development, put your own CurseForge API key in `local.properties` (never committed):

```properties
curseforge.apiKey=YOUR_KEY
```

A **release** build refuses to compile unless `proxy.url` and `proxy.token` point to a deployed proxy, so a
release never contains the API key. See [backend/README.md](backend/README.md) and
[docs/publicacion-play.md](docs/publicacion-play.md).

The tests include JVM screenshot tests of the UI at several screen sizes (`app/build/screenshots/`), and the
proxy has its own tests: `cd backend && node --test`.

## Licenses

Silkscreen font by the Silkscreen Project Authors, SIL Open Font License 1.1 (see `docs/licencias/`).
