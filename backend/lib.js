// Lógica pura del proxy (sin red), separada para poder probarla con `node --test`.

export const CF_API = "https://api.curseforge.com";
export const GAME_ID = "78022"; // Minecraft Bedrock
export const MAX_PAGE_SIZE = 50;
export const MAX_RESULTS = 10000; // límite de CurseForge: index + pageSize <= 10000
export const MAX_BULK_IDS = 50;
export const MAX_BODY_BYTES = 8 * 1024;

export const CDN_HOSTS = new Set(["edge.forgecdn.net", "mediafilez.forgecdn.net"]);
export const ALLOWED_EXTENSIONS = [".mcpack", ".mcaddon", ".mcworld", ".mctemplate"];

// Parámetros de búsqueda que se reenvían; todo lo demás se descarta.
const SEARCH_PARAMS = ["classId", "categoryId", "searchFilter", "sortField", "sortOrder", "pageSize", "index"];

/** Comparación en tiempo constante (para el token de la app). */
export function safeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function isInt(s, min, max) {
  return /^\d{1,9}$/.test(s) && Number(s) >= min && Number(s) <= max;
}

/** Construye la URL de búsqueda hacia CurseForge, o devuelve null si algún parámetro es inválido. */
export function buildSearchUrl(params) {
  const out = new URLSearchParams({ gameId: GAME_ID });
  for (const name of SEARCH_PARAMS) {
    const v = params.get(name);
    if (v === null) continue;
    let valid;
    switch (name) {
      case "searchFilter":
        valid = v.length <= 100;
        break;
      case "sortOrder":
        valid = v === "asc" || v === "desc";
        break;
      case "pageSize":
        valid = isInt(v, 1, MAX_PAGE_SIZE);
        break;
      case "index":
        valid = isInt(v, 0, MAX_RESULTS);
        break;
      case "sortField":
        valid = isInt(v, 1, 20);
        break;
      default: // classId, categoryId: ids de CurseForge, pueden pasar de 10000
        valid = isInt(v, 0, 999999);
    }
    if (!valid) return null;
    out.set(name, v);
  }
  const index = Number(out.get("index") ?? 0);
  const size = Number(out.get("pageSize") ?? MAX_PAGE_SIZE);
  if (index + size > MAX_RESULTS) return null;
  return `${CF_API}/v1/mods/search?${out}`;
}

/**
 * Valida una URL del CDN de CurseForge. Con [requireExtension] solo acepta archivos de Minecraft
 * (la descarga inicial); las redirecciones intermedias solo se validan por host.
 */
export function parseCdnUrl(raw, requireExtension = true) {
  let u;
  try {
    u = new URL(raw);
  } catch {
    return null;
  }
  if (u.protocol !== "https:" || !CDN_HOSTS.has(u.hostname) || u.username || u.password) return null;
  if (requireExtension) {
    let path;
    try {
      path = decodeURIComponent(u.pathname).toLowerCase();
    } catch {
      return null;
    }
    if (!ALLOWED_EXTENSIONS.some((ext) => path.endsWith(ext))) return null;
  }
  return u;
}

/** Valida y normaliza la lista de ids para la consulta por lotes. */
export function parseBulkIds(body) {
  const ids = body && body.modIds;
  if (!Array.isArray(ids) || ids.length === 0 || ids.length > MAX_BULK_IDS) return null;
  if (!ids.every((n) => Number.isInteger(n) && n > 0 && n < 2 ** 31)) return null;
  return ids;
}
