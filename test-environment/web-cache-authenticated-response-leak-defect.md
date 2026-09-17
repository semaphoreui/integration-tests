# Authenticated API responses can cross users through a shared HTTP cache

**Status:** confirmed on Semaphore `v2.19.12`  
**Severity:** P1 security defect when a shared cache is enabled for authenticated API traffic  
**Default installation:** not affected; Semaphore does not enable a shared cache itself  
**Confirmed:** 2026-09-16

## Summary

Semaphore's authenticated `GET /api/user` response does not declare `Cache-Control: private,
no-store`. A standards-compliant shared NGINX cache, configured without a cookie bypass, therefore
stores the response. Because a typical shared cache key does not include the session cookie, the
next user requesting the same URL receives the first user's response as a cache `HIT`.

The application does not ship this unsafe NGINX configuration. The defect is relevant to
self-hosted installations where an operator enables `proxy_cache`, a CDN, or another shared cache
in front of Semaphore without excluding authenticated and `/api` traffic.

## Reproduction

The isolated profile deliberately enables an observable shared cache whose key contains the
scheme, method, host, URI, and a test-only namespace, but not cookies, forwarding headers, or query
parameters:

```bash
test-environment/profile up feature-web-cache-safety
test-environment/profile test feature-web-cache-safety
```

The test creates disposable users with synthetic data and performs the following requests:

1. user A logs in and requests `GET /api/user`;
2. NGINX reports `X-Bookwright-Cache-Status: MISS` and stores the response;
3. user B logs in and requests the same path with a different session cookie;
4. NGINX reports `X-Bookwright-Cache-Status: HIT`;
5. the response body contains user A's ID, username, and email instead of user B's data.

A second scenario primes the same key with user A while sending `X-Forwarded-Host`,
`X-Original-URL`, `X-Rewrite-URL`, and a distinct query parameter. User B's request without those
values still receives user A's cached response.

## Expected result

Authenticated and user-specific responses must declare an explicit non-shared policy, for example:

```http
Cache-Control: private, no-store
```

The proxy must not store or reuse the response across sessions.

## Actual result

The direct authenticated response has no `Cache-Control` header. The strict local run produced:

| Check | Result |
|---|---|
| Direct `GET /api/user` policy | failed: `Cache-Control` absent |
| User A → user B boundary | failed: user B received user A, cache `HIT` |
| Unkeyed forwarding headers and query | failed: user B received user A, cache `HIT` |
| Host isolation | passed: `Host` is included in the test cache key |
| Versioned JavaScript asset | passed: explicit public policy and repeated `HIT` |
| `/swagger/api-docs.yml` | passed: explicit public policy and repeated `HIT` |

The run contains five tests: two safe public/cache-key boundaries pass and three strict security
contracts fail. The profile is a manual red reproducer and is not part of the stable PR or daily
matrix.

## Response-header inventory

| Response | Observed cache policy on `v2.19.12` | Assessment |
|---|---|---|
| authenticated `GET /api/user` | absent | unsafe behind an unpartitioned shared cache |
| unauthenticated `GET /api/user` (`401`) | absent | no user body, but no explicit policy |
| `GET /api/ping` | absent | public health response |
| SPA shell `/` | absent | public shell; no user data observed |
| missing API route (`404`) | absent | generic plain-text response |
| `/swagger/api-docs.yml` | `public` with `max-age` | intentionally cacheable |
| versioned `/js/...` asset | `public` with `max-age` | intentionally cacheable |

Password login is a `POST` and is not stored by the profile's GET/HEAD cache. The core password
profile has no OIDC redirect, so redirect-specific poisoning is outside this reproduction; the
confirmed authenticated API leak does not depend on either boundary.

## Impact

If a customer enables shared caching for `/api`, an authenticated response can expose another
user's account or project data. `/api/user` proves the cross-user boundary violation. Other
authenticated `GET` endpoints should be treated as potentially affected until an application-wide
response policy is verified.

## Recommended application fix

Set an application-level policy on authentication, session, and authenticated API responses:

```http
Cache-Control: private, no-store
```

Keep explicit long-lived public caching only for immutable/versioned assets and the public API
specification. `Vary: Cookie` is not an adequate substitute for `no-store` because it can create a
large cache of sensitive session-specific objects and relies on every intermediary honoring the
same key semantics.

## Safe reverse-proxy boundary

The simplest deployment rule is to disable shared caching for the entire API and enable it only for
known public resources:

```nginx
location ^~ /api/ {
  proxy_pass http://semaphore:3000;
  proxy_cache off;
}

location ~* ^/(js|css|img|fonts)/ {
  proxy_pass http://semaphore:3000;
  proxy_cache semaphore_public;
  proxy_cache_valid 200 10m;
}

location = /swagger/api-docs.yml {
  proxy_pass http://semaphore:3000;
  proxy_cache semaphore_public;
  proxy_cache_valid 200 10m;
}
```

If a broader cache is unavoidable, both lookup and storage must be bypassed for a Semaphore session
cookie:

```nginx
map $http_cookie $has_semaphore_session {
  default 0;
  ~*(^|;[[:space:]]*)semaphore= 1;
}

proxy_cache_bypass $has_semaphore_session;
proxy_no_cache $has_semaphore_session $upstream_http_set_cookie;
```

Application headers remain necessary because Semaphore cannot assume every deployment uses NGINX or
has a correct proxy configuration.

## Fix verification

After an upstream fix, the same profile must become green without weakening the proxy:

- the direct authenticated response contains `private, no-store`;
- both users receive their own account data and the second response is not a shared-cache `HIT`;
- unkeyed header/query priming does not cross the user boundary;
- Host remains isolated;
- versioned assets and the public API specification remain cacheable.
