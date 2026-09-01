/**
 * Serves objects from an R2 bucket, but only to requests carrying a valid
 * Cloudflare Access JWT (issued after the user logs in with an email
 * one-time PIN). Unauthenticated or forged requests get 401/403.
 *
 * Bindings (set via Terraform):
 *   BUCKET             R2 bucket
 *   ACCESS_TEAM_DOMAIN e.g. myteam.cloudflareaccess.com
 *   ACCESS_AUD         Access application audience tag
 *   INDEX_DOCUMENT     e.g. index.html
 */

const JWKS_TTL_MS = 60 * 60 * 1000;
let jwksCache = { fetchedAt: 0, keys: [] };

export default {
  async fetch(request, env) {
    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method not allowed", { status: 405 });
    }

    const token =
      request.headers.get("Cf-Access-Jwt-Assertion") ||
      getCookie(request, "CF_Authorization");
    if (!token) {
      return new Response("Unauthorized: missing Cloudflare Access token", { status: 401 });
    }

    let claims;
    try {
      claims = await verifyAccessJwt(token, env.ACCESS_TEAM_DOMAIN, env.ACCESS_AUD);
    } catch (err) {
      return new Response(`Forbidden: ${err.message}`, { status: 403 });
    }

    return serveFromBucket(request, env, claims);
  },
};

async function serveFromBucket(request, env, claims) {
  const url = new URL(request.url);
  let key = decodeURIComponent(url.pathname.replace(/^\/+/, ""));
  if (key === "" || key.endsWith("/")) key += env.INDEX_DOCUMENT;

  let object = await env.BUCKET.get(key);

  // "/docs" -> "/docs/index.html"
  if (object === null && !key.includes(".")) {
    object = await env.BUCKET.get(`${key}/${env.INDEX_DOCUMENT}`);
  }
  if (object === null) {
    return new Response("Not found", { status: 404 });
  }

  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("etag", object.httpEtag);
  headers.set("cache-control", "private, no-store");
  headers.set("x-authenticated-user", claims.email || "");

  if (request.headers.get("if-none-match") === object.httpEtag) {
    return new Response(null, { status: 304, headers });
  }
  return new Response(request.method === "HEAD" ? null : object.body, { headers });
}

// ---------------------------------------------------------------------------
// Cloudflare Access JWT verification (RS256 against the team's JWKS)
// ---------------------------------------------------------------------------

async function verifyAccessJwt(token, teamDomain, audience) {
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("malformed token");

  const [headerB64, payloadB64, signatureB64] = parts;
  const header = JSON.parse(b64urlDecodeToString(headerB64));
  const payload = JSON.parse(b64urlDecodeToString(payloadB64));

  if (header.alg !== "RS256") throw new Error("unexpected alg");

  const jwk = await getSigningKey(teamDomain, header.kid);
  const cryptoKey = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"],
  );

  const ok = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    b64urlDecode(signatureB64),
    new TextEncoder().encode(`${headerB64}.${payloadB64}`),
  );
  if (!ok) throw new Error("invalid signature");

  const now = Math.floor(Date.now() / 1000);
  if (typeof payload.exp !== "number" || payload.exp < now) throw new Error("token expired");
  if (typeof payload.nbf === "number" && payload.nbf > now) throw new Error("token not yet valid");

  const aud = Array.isArray(payload.aud) ? payload.aud : [payload.aud];
  if (!aud.includes(audience)) throw new Error("audience mismatch");

  if (payload.iss !== `https://${teamDomain}`) throw new Error("issuer mismatch");

  return payload;
}

async function getSigningKey(teamDomain, kid) {
  const stale = Date.now() - jwksCache.fetchedAt > JWKS_TTL_MS;
  let key = stale ? undefined : jwksCache.keys.find((k) => k.kid === kid);

  if (!key) {
    const res = await fetch(`https://${teamDomain}/cdn-cgi/access/certs`, {
      cf: { cacheTtl: 300, cacheEverything: true },
    });
    if (!res.ok) throw new Error(`cannot fetch JWKS (${res.status})`);
    const { keys } = await res.json();
    jwksCache = { fetchedAt: Date.now(), keys };
    key = keys.find((k) => k.kid === kid);
  }
  if (!key) throw new Error("unknown signing key");
  return key;
}

function getCookie(request, name) {
  const cookie = request.headers.get("Cookie") || "";
  const match = cookie.match(new RegExp(`(?:^|;\\s*)${name}=([^;]*)`));
  return match ? match[1] : null;
}

function b64urlDecode(str) {
  const pad = "=".repeat((4 - (str.length % 4)) % 4);
  const bin = atob((str + pad).replace(/-/g, "+").replace(/_/g, "/"));
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

function b64urlDecodeToString(str) {
  return new TextDecoder().decode(b64urlDecode(str));
}
