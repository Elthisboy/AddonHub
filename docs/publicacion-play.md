# Checklist para publicar en Google Play

No es asesoría legal. Verifica cada punto en las políticas vigentes de Google Play antes de enviar.

## 1. Antes de compilar
- [ ] Key de CurseForge aprobada y proxy desplegado (`backend/README.md`).
- [ ] `local.properties` con `proxy.url` y `proxy.token`.
- [ ] Probado en un teléfono real: instalación, dependencias, descarga en segundo plano, modo padres.
- [ ] Texto de "app no oficial, no afiliada a Mojang ni Microsoft" visible en la ficha de la tienda.
- [ ] Nombre de la app sin la marca "Minecraft" como nombre principal.

## 2. Firma (una sola vez)
Crea tu llave de subida **con tus propias contraseñas** y guárdala en un lugar seguro (si la pierdes,
no podrás actualizar la app salvo que uses Play App Signing y pidas un restablecimiento):

```bash
keytool -genkeypair -v -keystore addonhub-upload.jks -alias addonhub -keyalg RSA -keysize 2048 -validity 10000
```

Crea `keystore.properties` en la raíz (ya está en `.gitignore`; no lo subas a ningún sitio):

```properties
storeFile=addonhub-upload.jks
storePassword=...
keyAlias=addonhub
keyPassword=...
```

Actívalo en Play Console con **Play App Signing** (recomendado).

## 3. Compilar
```bash
./gradlew bundleRelease       # genera app/build/outputs/bundle/release/app-release.aab
```
Sube el `.aab` a Play Console. El release usa R8 y no lleva la API key.

## 4. Play Console
- [ ] **Público objetivo:** niños (y el rango de edad) → entra en el programa **Families**. Revisa la
      [política de Families](https://support.google.com/googleplay/android-developer/answer/9900633).
- [ ] **Sin anuncios.** Si algún día se añaden, solo SDKs certificados para niños
      ([política](https://support.google.com/googleplay/android-developer/answer/12918983)).
- [ ] **Seguridad de los datos:** declara lo que realmente pasa (ver `politica-de-privacidad.md`):
      sin cuentas, sin datos personales; las búsquedas pasan por tu servidor intermediario sin guardarse.
- [ ] **URL de la política de privacidad** pública.
- [ ] **Clasificación de contenido:** responde el cuestionario con honestidad. El contenido es de terceros
      y se filtra por edad.
- [ ] **Modo adulto escondido:** una función distinta oculta en una app para niños puede complicar la
      revisión o el programa Families. Valora quitarlo del release público (dejarlo solo para pruebas),
      o descríbelo con claridad al enviar la app a revisión.
- [ ] **Permiso de notificaciones y servicio en primer plano (`dataSync`):** declara su uso (descargas).
- [ ] **Pruebas:** las cuentas personales nuevas pueden requerir una prueba cerrada con varios testers
      durante un tiempo antes de producción. Comprueba el requisito vigente.
- [ ] Ficha en **dos idiomas**: inglés (predeterminado) y español. Usa `store/icon-512.png` y, de cada
      idioma, `store/<en|es>/feature-graphic-1024x500.png` y las 4 capturas de `store/<en|es>/phone/`.
      Redacta la descripción corta (80 caracteres) y la larga en ambos idiomas, con el aviso de
      "app no oficial, no afiliada a Mojang ni Microsoft".

## 5. Después de publicar
- [ ] Rate limiting en Cloudflare sobre el Worker.
- [ ] Revisar los reportes de fallos y los comentarios (palabras que el filtro deje pasar).
- [ ] Mantener los créditos y dar de baja de inmediato cualquier contenido que un autor pida retirar.
