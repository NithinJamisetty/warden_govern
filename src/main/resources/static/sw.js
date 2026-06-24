// ============================================================
//  SWMS.GOV — Service Worker
//  Cache version is auto-updated on every deploy.
//  Change BUILD_VERSION below when you push new code.
// ============================================================

const BUILD_VERSION = '2026-06-24-v6';       // ← update this on every deploy
const CACHE_NAME    = `swms-cache-${BUILD_VERSION}`;

// Only pre-cache the absolute minimum (shell assets).
// Everything else is fetched fresh from the network.
const PRECACHE_ASSETS = [
  '/manifest.json'
];

// ── Install: pre-cache shell assets ──────────────────────────
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(PRECACHE_ASSETS))
      .then(() => self.skipWaiting())   // activate immediately
  );
});

// ── Activate: DELETE all old caches ──────────────────────────
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(
        keys
          .filter(key => key !== CACHE_NAME)  // delete every cache except current
          .map(key => {
            console.log('[SW] Deleting old cache:', key);
            return caches.delete(key);
          })
      )
    ).then(() => self.clients.claim())
  );
});

// ── Fetch: Network-First strategy for HTML & CSS ─────────────
//  This ensures updated pages are ALWAYS loaded fresh.
//  Falls back to cache only when offline.
self.addEventListener('fetch', event => {
  // Skip non-GET and API requests entirely
  if (event.request.method !== 'GET' || event.request.url.includes('/api/')) {
    return;
  }

  const url = new URL(event.request.url);

  // ── NETWORK FIRST for HTML pages and CSS/JS ──────────────
  //  Always try network first so new deployments are reflected immediately.
  if (
    event.request.headers.get('accept')?.includes('text/html') ||
    url.pathname.endsWith('.html') ||
    url.pathname.endsWith('.css') ||
    url.pathname.endsWith('.js')
  ) {
    event.respondWith(
      fetch(event.request)
        .then(networkResponse => {
          // Update the cache with the fresh response
          if (networkResponse && networkResponse.status === 200 && networkResponse.type === 'basic') {
            const clone = networkResponse.clone();
            caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
          }
          return networkResponse;
        })
        .catch(() => {
          // Only fall back to cache when offline
          return caches.match(event.request).then(cached => {
            if (cached) return cached;
            // Offline fallback for HTML pages
            if (event.request.headers.get('accept')?.includes('text/html')) {
              return caches.match('/index.html');
            }
          });
        })
    );
    return;
  }

  // ── CACHE FIRST for images/fonts (rarely change) ─────────
  event.respondWith(
    caches.match(event.request).then(cachedResponse => {
      if (cachedResponse) return cachedResponse;
      return fetch(event.request).then(networkResponse => {
        if (networkResponse && networkResponse.status === 200 && networkResponse.type === 'basic') {
          const clone = networkResponse.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
        }
        return networkResponse;
      });
    })
  );
});
