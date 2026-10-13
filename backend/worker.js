// Proxy de AddonHub para la API de CurseForge (Cloudflare Worker).
//
// Para qué sirve:
//  - La API key vive solo aquí (secreto CF_API_KEY), nunca dentro del APK.
//  - Solo deja pasar las pocas consultas que usa la app, siempre de Minecraft Bedrock.
//  - Las descargas del CDN de CurseForge (que exigen la key) pasan por /dl, también restringidas.
//  - No guarda nada: ni caché ni registros de contenido (los términos de la API lo prohíben).
//
// Secretos (npx wrangler secret put ...):  CF_API_KEY, APP_TOKEN

import {
  CF_API,
  GAME_ID,
  MAX_BODY_BYTES,
  buildSearchUrl,
  parseBulkIds,
  parseCdnUrl,
  safeEqual,
} from "./lib.js";

const NO_STORE = { "Cache-Control": "no-store" };
const MAX_REDIRECTS = 3;

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", ...NO_STORE },
  });
}

async function readJsonBody(request) {
  const text = await request.text();
  if (text.length > MAX_BODY_BYTES) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

/** Reenvía una consulta a la API de CurseForge añadiendo la key. */
async function forwardApi(url, env, init = {}) {
  const upstream = await fetch(url, {
    method: init.method || "GET",
    body: init.body,
    headers: {
      "x-api-key": env.CF_API_KEY,
      Accept: "application/json",
      "User-Agent": "AddonHub-Proxy",
      ...(init.headers || {}),
    },
    cf: { cacheTtl: 0, cacheEverything: false },
  });
  const headers = new Headers(NO_STORE);
  headers.set("Content-Type", upstream.headers.get("Content-Type") || "application/json");
  return new Response(upstream.body, { status: upstream.status, headers });
}

/**
 * Descarga del CDN. Las redirecciones se siguen a mano y solo hacia el propio CDN de CurseForge,
 * para que la key nunca se envíe a otro sitio.
 */
async function download(request, env, first) {
  const base = { "x-api-key": env.CF_API_KEY, "User-Agent": "AddonHub-Proxy" };
  const rangeHeader = request.headers.get("Range");
  const range = rangeHeader && /^bytes=\d*-\d*$/.test(rangeHeader) ? rangeHeader : null;
  const get = (url, withRange) =>
    fetch(url.toString(), {
      headers: withRange && range ? { ...base, Range: range } : base,
      redirect: "manual",
      cf: { cacheTtl: 0, cacheEverything: false },
    });

  let target = first;
  for (let hop = 0; hop <= MAX_REDIRECTS; hop++) {
    // El CDN rechaza "Range" en el primer salto (404): solo vale en el host que entrega el archivo.
    let upstream = await get(target, hop > 0);
    if (upstream.status >= 300 && upstream.status < 400) {
      const location = upstream.headers.get("Location");
      const next = location ? parseCdnUrl(new URL(location, target).toString(), false) : null;
      if (!next) return json({ error: "bad_redirect" }, 502);
      target = next;
      continue;
    }
    // Sin redirección en el primer salto: ese ya es el host final, así que se repite con Range.
    if (range && hop === 0 && upstream.status === 200) {
      await upstream.body?.cancel();
      upstream = await get(target, true);
    }
    const out = new Headers(NO_STORE);
    for (const h of ["Content-Type", "Content-Length", "Content-Range", "Accept-Ranges"]) {
      const v = upstream.headers.get(h);
      if (v) out.set(h, v);
    }
    return new Response(upstream.body, { status: upstream.status, headers: out });
  }
  return json({ error: "too_many_redirects" }, 502);
}

async function handle(request, env) {
  // Sin secretos configurados, nunca se deja pasar a nadie (evita "token vacío == token vacío").
  if (!env.APP_TOKEN || !env.CF_API_KEY) return json({ error: "misconfigured" }, 500);
  if (!safeEqual(request.headers.get("X-App-Token") || "", env.APP_TOKEN)) {
    return json({ error: "unauthorized" }, 401);
  }

  const url = new URL(request.url);
  const { pathname } = url;

  if (request.method === "GET") {
    if (pathname === "/v1/mods/search") {
      const target = buildSearchUrl(url.searchParams);
      return target ? forwardApi(target, env) : json({ error: "bad_request" }, 400);
    }
    if (pathname === "/v1/categories") {
      return forwardApi(`${CF_API}/v1/categories?gameId=${GAME_ID}`, env);
    }
    const desc = pathname.match(/^\/v1\/mods\/(\d{1,9})\/description$/);
    if (desc) return forwardApi(`${CF_API}/v1/mods/${desc[1]}/description`, env);

    if (pathname === "/dl") {
      const target = parseCdnUrl(url.searchParams.get("u") || "");
      return target ? download(request, env, target) : json({ error: "bad_request" }, 400);
    }
  }

  if (request.method === "POST") {
    if (pathname === "/v1/mods") {
      const ids = parseBulkIds(await readJsonBody(request));
      if (!ids) return json({ error: "bad_request" }, 400);
      return forwardApi(`${CF_API}/v1/mods`, env, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ modIds: ids, filterPcOnly: false }),
      });
    }
    if (pathname === "/v1/mods/featured") {
      return forwardApi(`${CF_API}/v1/mods/featured`, env, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ gameId: Number(GAME_ID), excludedModIds: [], gameVersionTypeId: null }),
      });
    }
  }

  return json({ error: "not_found" }, 404);
}

export default {
  async fetch(request, env) {
    try {
      return await handle(request, env);
    } catch {
      return json({ error: "proxy_error" }, 502);
    }
  },
};
