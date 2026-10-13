// Pruebas del Worker completo con `fetch` simulado (no toca internet). Ejecutar con:  node --test
import test from "node:test";
import assert from "node:assert/strict";
import worker from "./worker.js";

const ENV = { CF_API_KEY: "KEY-SECRETA", APP_TOKEN: "token-de-la-app-123" };
const BASE = "https://proxy.test";

/** Sustituye fetch por un simulador y devuelve las llamadas hechas a "CurseForge". */
function mockUpstream(handler) {
  const calls = [];
  globalThis.fetch = async (url, init = {}) => {
    calls.push({ url: String(url), init });
    return handler(String(url), init, calls.length);
  };
  return calls;
}

const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), { status, headers: { "Content-Type": "application/json" } });

function req(path, { method = "GET", token = ENV.APP_TOKEN, headers = {}, body } = {}) {
  const h = { ...headers };
  if (token !== null) h["X-App-Token"] = token;
  return new Request(BASE + path, { method, headers: h, body });
}

const call = (request, env = ENV) => worker.fetch(request, env);

test("sin secretos configurados responde 500 y no llama a nadie", async () => {
  const calls = mockUpstream(() => json({}));
  const res = await call(req("/v1/categories"), {});
  assert.equal(res.status, 500);
  assert.equal(calls.length, 0);
});

test("token ausente o incorrecto responde 401 y no llama a CurseForge", async () => {
  const calls = mockUpstream(() => json({}));
  assert.equal((await call(req("/v1/categories", { token: null }))).status, 401);
  assert.equal((await call(req("/v1/categories", { token: "otro" }))).status, 401);
  assert.equal(calls.length, 0);
});

test("token vacío no coincide ni siquiera con un APP_TOKEN vacío", async () => {
  mockUpstream(() => json({}));
  const res = await call(req("/v1/categories", { token: "" }), { CF_API_KEY: "k", APP_TOKEN: "" });
  assert.equal(res.status, 500); // sin APP_TOKEN el Worker se considera mal configurado
});

test("búsqueda: reenvía con la key, fuerza Bedrock y descarta parámetros ajenos", async () => {
  const calls = mockUpstream(() => json({ data: [{ id: 1 }], pagination: { totalCount: 1 } }));
  const res = await call(req("/v1/mods/search?gameId=432&classId=4984&searchFilter=dragon&evil=1&pageSize=20"));
  assert.equal(res.status, 200);
  assert.equal(res.headers.get("Cache-Control"), "no-store");
  assert.deepEqual((await res.json()).data, [{ id: 1 }]);
  assert.equal(calls.length, 1);
  const url = new URL(calls[0].url);
  assert.equal(url.origin + url.pathname, "https://api.curseforge.com/v1/mods/search");
  assert.equal(url.searchParams.get("gameId"), "78022");
  assert.equal(url.searchParams.has("evil"), false);
  assert.equal(calls[0].init.headers["x-api-key"], "KEY-SECRETA");
});

test("búsqueda inválida responde 400 sin llamar a CurseForge", async () => {
  const calls = mockUpstream(() => json({}));
  assert.equal((await call(req("/v1/mods/search?pageSize=999"))).status, 400);
  assert.equal(calls.length, 0);
});

test("el estado de CurseForge se transmite tal cual (p. ej. 403)", async () => {
  mockUpstream(() => new Response("Forbidden", { status: 403, headers: { "Content-Type": "text/plain" } }));
  const res = await call(req("/v1/mods/search?classId=4984"));
  assert.equal(res.status, 403);
});

test("categorías: siempre de Bedrock", async () => {
  const calls = mockUpstream(() => json({ data: [] }));
  await call(req("/v1/categories?gameId=1"));
  assert.equal(new URL(calls[0].url).searchParams.get("gameId"), "78022");
});

test("descripción: solo ids numéricos", async () => {
  const calls = mockUpstream(() => json({ data: "<p>hola</p>" }));
  assert.equal((await call(req("/v1/mods/1504044/description"))).status, 200);
  assert.equal(calls[0].url, "https://api.curseforge.com/v1/mods/1504044/description");
  assert.equal((await call(req("/v1/mods/abc/description"))).status, 404);
});

test("varios mods: reenvía ids válidos y rechaza lo demás", async () => {
  const calls = mockUpstream(() => json({ data: [] }));
  const ok = await call(req("/v1/mods", { method: "POST", body: JSON.stringify({ modIds: [1, 2], extra: "x" }) }));
  assert.equal(ok.status, 200);
  assert.deepEqual(JSON.parse(calls[0].init.body), { modIds: [1, 2], filterPcOnly: false });
  assert.equal(calls[0].init.method, "POST");
  const bad = await call(req("/v1/mods", { method: "POST", body: JSON.stringify({ modIds: "x" }) }));
  assert.equal(bad.status, 400);
  const notJson = await call(req("/v1/mods", { method: "POST", body: "no es json" }));
  assert.equal(notJson.status, 400);
  assert.equal(calls.length, 1);
});

test("destacados: ignora el cuerpo del cliente y fuerza Bedrock", async () => {
  const calls = mockUpstream(() => json({ data: {} }));
  await call(req("/v1/mods/featured", { method: "POST", body: JSON.stringify({ gameId: 432 }) }));
  assert.equal(JSON.parse(calls[0].init.body).gameId, 78022);
});

test("descarga: rechaza hosts ajenos y extensiones no permitidas sin llamar a nadie", async () => {
  const calls = mockUpstream(() => new Response("x"));
  const enc = encodeURIComponent;
  assert.equal((await call(req(`/dl?u=${enc("https://example.com/a.mcaddon")}`))).status, 400);
  assert.equal((await call(req(`/dl?u=${enc("https://edge.forgecdn.net/a.exe")}`))).status, 400);
  assert.equal((await call(req("/dl"))).status, 400);
  assert.equal(calls.length, 0);
});

/** Simula el CDN real: edge redirige (y da 404 si llega con Range); mediafilez entrega el archivo. */
function realisticCdn() {
  return mockUpstream((url, init) => {
    const range = init.headers?.Range;
    if (url.startsWith("https://edge.forgecdn.net/")) {
      if (range) return new Response(null, { status: 404 });
      return new Response(null, { status: 302, headers: { Location: "https://mediafilez.forgecdn.net/files/1/2/a.mcaddon" } });
    }
    if (range) {
      return new Response("CONTE", {
        status: 206,
        headers: { "Content-Type": "application/octet-stream", "Content-Range": "bytes 0-4/9", "Set-Cookie": "x=1" },
      });
    }
    return new Response("CONTENIDO", {
      status: 200,
      headers: { "Content-Type": "application/octet-stream", "Content-Length": "9", "Accept-Ranges": "bytes", "Set-Cookie": "x=1" },
    });
  });
}

const CDN_URL = encodeURIComponent("https://edge.forgecdn.net/files/1/2/Mi%20addon.mcaddon");

test("descarga: pasa por la redirección del CDN con la key y entrega el archivo", async () => {
  const calls = realisticCdn();
  const res = await call(req(`/dl?u=${CDN_URL}`));
  assert.equal(res.status, 200);
  assert.equal(await res.text(), "CONTENIDO");
  assert.equal(res.headers.get("Accept-Ranges"), "bytes");
  assert.equal(res.headers.get("Set-Cookie"), null); // no se filtran cabeceras ajenas
  assert.equal(calls.length, 2);
  for (const c of calls) {
    assert.equal(c.init.headers["x-api-key"], "KEY-SECRETA");
    assert.equal(c.init.redirect, "manual");
  }
});

test("descarga con Range: el Range viaja solo al host final (el primer salto lo rechaza)", async () => {
  const calls = realisticCdn();
  const res = await call(req(`/dl?u=${CDN_URL}`, { headers: { Range: "bytes=0-4" } }));
  assert.equal(res.status, 206);
  assert.equal(await res.text(), "CONTE");
  assert.equal(res.headers.get("Content-Range"), "bytes 0-4/9");
  assert.equal(calls[0].init.headers.Range, undefined); // edge: sin Range
  assert.equal(calls[1].init.headers.Range, "bytes=0-4"); // mediafilez: con Range
});

test("descarga con Range: si el primer host ya entrega el archivo, se repite con Range", async () => {
  const calls = mockUpstream((url, init) =>
    init.headers?.Range ? new Response("PARTE", { status: 206 }) : new Response("COMPLETO", { status: 200 }),
  );
  const res = await call(req(`/dl?u=${CDN_URL}`, { headers: { Range: "bytes=2-6" } }));
  assert.equal(res.status, 206);
  assert.equal(await res.text(), "PARTE");
  assert.equal(calls.length, 2);
  assert.equal(calls[0].init.headers.Range, undefined);
  assert.equal(calls[1].init.headers.Range, "bytes=2-6");
});

test("descarga: ignora un Range malformado", async () => {
  const calls = mockUpstream(() => new Response("x"));
  await call(req(`/dl?u=${CDN_URL}`, { headers: { Range: "evil" } }));
  assert.equal(calls.length, 1);
  assert.equal(calls[0].init.headers.Range, undefined);
});

test("descarga: sigue una redirección dentro del CDN llevando la key", async () => {
  const calls = mockUpstream((url) =>
    url.startsWith("https://edge.forgecdn.net/")
      ? new Response(null, { status: 302, headers: { Location: "https://mediafilez.forgecdn.net/real/archivo" } })
      : new Response("OK"),
  );
  const res = await call(req(`/dl?u=${encodeURIComponent("https://edge.forgecdn.net/a.mcworld")}`));
  assert.equal(res.status, 200);
  assert.equal(await res.text(), "OK");
  assert.equal(calls.length, 2);
  assert.equal(calls[1].url, "https://mediafilez.forgecdn.net/real/archivo");
  assert.equal(calls[1].init.headers["x-api-key"], "KEY-SECRETA");
});

test("descarga: una redirección a otro sitio se corta y la key NUNCA se envía allí", async () => {
  const calls = mockUpstream(() => new Response(null, { status: 302, headers: { Location: "https://evil.example/robar" } }));
  const res = await call(req(`/dl?u=${encodeURIComponent("https://edge.forgecdn.net/a.mcaddon")}`));
  assert.equal(res.status, 502);
  assert.equal(calls.length, 1);
  assert.ok(!calls.some((c) => c.url.includes("evil.example")));
});

test("descarga: demasiadas redirecciones responden 502", async () => {
  mockUpstream(() => new Response(null, { status: 302, headers: { Location: "https://edge.forgecdn.net/otra" } }));
  const res = await call(req(`/dl?u=${encodeURIComponent("https://edge.forgecdn.net/a.mcaddon")}`));
  assert.equal(res.status, 502);
});

test("rutas y métodos no permitidos responden 404", async () => {
  const calls = mockUpstream(() => json({}));
  assert.equal((await call(req("/v1/games"))).status, 404);
  assert.equal((await call(req("/v1/categories", { method: "POST", body: "{}" }))).status, 404);
  assert.equal((await call(req("/"))).status, 404);
  assert.equal(calls.length, 0);
});

test("si CurseForge falla de red responde 502 sin filtrar detalles", async () => {
  mockUpstream(() => {
    throw new Error("secreto interno KEY-SECRETA");
  });
  const res = await call(req("/v1/categories"));
  assert.equal(res.status, 502);
  assert.equal((await res.text()).includes("KEY-SECRETA"), false);
});
