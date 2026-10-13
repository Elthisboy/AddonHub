// Pruebas de la lógica pura del proxy. Ejecutar con:  node --test
import test from "node:test";
import assert from "node:assert/strict";
import { buildSearchUrl, parseBulkIds, parseCdnUrl, safeEqual } from "./lib.js";

const q = (s) => new URLSearchParams(s);

test("búsqueda: fuerza Bedrock y descarta parámetros desconocidos", () => {
  const url = new URL(buildSearchUrl(q("gameId=432&classId=4984&evil=1&searchFilter=dragon&pageSize=50")));
  assert.equal(url.searchParams.get("gameId"), "78022");
  assert.equal(url.searchParams.get("classId"), "4984");
  assert.equal(url.searchParams.get("searchFilter"), "dragon");
  assert.equal(url.searchParams.has("evil"), false);
});

test("búsqueda: acepta categoryId mayores a 10000", () => {
  assert.ok(buildSearchUrl(q("categoryId=11333&classId=6929")));
});

test("búsqueda: rechaza valores inválidos o fuera de rango", () => {
  assert.equal(buildSearchUrl(q("pageSize=51")), null);
  assert.equal(buildSearchUrl(q("pageSize=0")), null);
  assert.equal(buildSearchUrl(q("index=9990&pageSize=50")), null);
  assert.equal(buildSearchUrl(q("sortOrder=sideways")), null);
  assert.equal(buildSearchUrl(q("classId=abc")), null);
  assert.equal(buildSearchUrl(q("searchFilter=" + "a".repeat(101))), null);
});

test("descarga: solo CDN de CurseForge y solo archivos de Minecraft", () => {
  assert.ok(parseCdnUrl("https://edge.forgecdn.net/files/1/2/Mi%20addon.mcaddon"));
  assert.ok(parseCdnUrl("https://mediafilez.forgecdn.net/files/1/2/a.MCPACK"));
  assert.equal(parseCdnUrl("https://example.com/a.mcaddon"), null);
  assert.equal(parseCdnUrl("http://edge.forgecdn.net/a.mcaddon"), null);
  assert.equal(parseCdnUrl("https://edge.forgecdn.net/a.exe"), null);
  assert.equal(parseCdnUrl("https://edge.forgecdn.net.evil.com/a.mcaddon"), null);
  assert.equal(parseCdnUrl("https://user:pw@edge.forgecdn.net/a.mcaddon"), null);
  assert.equal(parseCdnUrl("no es una url"), null);
});

test("descarga: las redirecciones se validan solo por host", () => {
  assert.ok(parseCdnUrl("https://edge.forgecdn.net/algo/sin-extension", false));
  assert.equal(parseCdnUrl("https://evil.com/algo", false), null);
});

test("lotes: valida la lista de ids", () => {
  assert.deepEqual(parseBulkIds({ modIds: [1, 2, 3] }), [1, 2, 3]);
  assert.equal(parseBulkIds({ modIds: [] }), null);
  assert.equal(parseBulkIds({ modIds: ["1"] }), null);
  assert.equal(parseBulkIds({ modIds: [-1] }), null);
  assert.equal(parseBulkIds({ modIds: Array.from({ length: 51 }, (_, i) => i + 1) }), null);
  assert.equal(parseBulkIds(null), null);
});

test("token: comparación segura", () => {
  assert.equal(safeEqual("abc", "abc"), true);
  assert.equal(safeEqual("abc", "abd"), false);
  assert.equal(safeEqual("abc", "abcd"), false);
  assert.equal(safeEqual(undefined, "abc"), false);
});
