# RSA Read-Only REST API

The RSA (Routing and Spectrum Assignment) app exposes a read-only REST surface
under `/onos/rsa` so an external web UI (e.g. served behind nginx) can display
routing and spectrum-occupancy state.

- Base path: `/onos/rsa`
- Auth: HTTP Basic (`onos:rocks` by default), same as all ONOS REST APIs.
- Reachability: the ONOS REST service is ClusterIP-only. From outside the
  cluster, front it with a reverse proxy (see the nginx section below).
- JSON: all field names are camelCase.

## Endpoints

### Read-only (added by this feature)

| Method | Path                  | Description |
|--------|-----------------------|-------------|
| GET    | `/onos/rsa/paths`     | All active RSA path decisions. |
| GET    | `/onos/rsa/spectrum`  | Per-link spectrum occupancy snapshot. |
| GET    | `/onos/rsa/topology`  | Devices (nodes) and active links with per-link utilization. |
| GET    | `/onos/rsa/stats`     | Aggregate spectrum/utilization KPIs. |

### Legacy (unchanged)

| Method | Path                      | Success | Description |
|--------|---------------------------|---------|-------------|
| POST   | `/onos/rsa/path`          | 201     | Submit a demand; returns the RSA decision. |
| GET    | `/onos/rsa/path/{id}`     | 200     | Fetch a single decision by requestId. |
| DELETE | `/onos/rsa/path/{id}`     | 204     | Release a demand (withdraw + free slots). |

### Response shapes (representative)

`GET /onos/rsa/paths`

```json
{
  "paths": [
    {
      "requestId": "1",
      "blocked": false,
      "src": "rest:10.233.57.80:8080/1",
      "dst": "rest:10.233.20.6:8080/1",
      "startSlot": 0,
      "width": 4,
      "devices": ["rest:10.233.57.80:8080", "rest:10.233.52.212:8080", "rest:10.233.20.6:8080"],
      "links": [
        {"src": "rest:10.233.57.80:8080/3", "dst": "rest:10.233.52.212:8080/2"},
        {"src": "rest:10.233.52.212:8080/3", "dst": "rest:10.233.20.6:8080/2"}
      ],
      "ochSignal": {"channelSpacing": "CHL_50GHZ", "gridType": "DWDM", "slotGranularity": 4, "spacingMultiplier": 0}
    }
  ]
}
```

Each `paths[]` entry uses the same encoding as a single `GET /onos/rsa/path/{id}` result.

`GET /onos/rsa/spectrum`

```json
{
  "slotCount": 16,
  "links": [
    {
      "src": "rest:10.233.52.212:8080/2",
      "dst": "rest:10.233.57.80:8080/3",
      "occupiedSlots": [0, 1, 2, 3],
      "freeSlotCount": 12,
      "maxContiguousFree": 12,
      "reservations": [{"requestId": "1", "startSlot": 0, "width": 4}]
    }
  ]
}
```

`GET /onos/rsa/topology`

```json
{
  "nodes": [{"id": "rest:10.233.20.6:8080", "name": "rest:10.233.20.6:8080", "type": "ROADM"}],
  "links": [{"src": "rest:10.233.20.6:8080/3", "dst": "rest:10.233.60.231:8080/3", "capacity": 16, "used": 0, "utilization": 0.0}]
}
```

`GET /onos/rsa/stats`

```json
{
  "totalLinks": 2,
  "totalSlots": 32,
  "usedSlots": 8,
  "overallUtilization": 0.25,
  "activeRequests": 1,
  "blockedCount": 0,
  "maxContiguousFree": 12,
  "averageFragmentation": 0.0
}
```

## CORS design decision and limitation

The RSA app registers `RsaCorsFilter` (a JAX-RS `ContainerResponseFilter`)
scoped to the `/onos/rsa` context only. It stamps the following headers on every
RSA response:

```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, Authorization
```

Verified live on the running controller:

- An authenticated cross-origin `GET` (e.g. with `Origin: http://example.com`)
  returns `200` and carries `Access-Control-Allow-Origin`. The actual
  data-reading requests a browser makes therefore work cross-origin today.

Known limitation — the browser `OPTIONS` preflight:

- A CORS preflight is sent by the browser **without credentials**. ONOS's
  **controller-wide** Karaf/Jersey authentication filter rejects any
  unauthenticated request with `401 Unauthorized` **before** it reaches the RSA
  JAX-RS application. As a result, `RsaCorsFilter` (and any RSA-app-scoped
  preflight resource) never runs on the preflight, and the preflight response
  carries no CORS headers. With credentials, `OPTIONS /onos/rsa/stats` returns
  `200` with the CORS headers — so this is purely the unauthenticated-preflight
  path.
- This cannot be fixed inside the RSA app. The only ONOS-side fix would be to
  loosen authentication for `OPTIONS` across **every** ONOS REST API, which
  changes the whole controller's security posture. That is out of scope for this
  read-only, purely additive feature.

Resolution: terminate the CORS preflight at the reverse proxy (nginx). This is
the standard reverse-proxy CORS pattern: nginx answers `OPTIONS` directly and
forwards the real (authenticated) requests to ONOS, whose responses already
carry the RSA CORS headers.

## nginx reverse-proxy example

The proxy answers the browser's `OPTIONS` preflight itself, injects the
`Authorization` header on forwarded requests (so the browser never handles ONOS
credentials), and ensures `Access-Control-Allow-Origin` is present on responses.
Replace `ONOS_HOST:8181` with how your nginx reaches the ONOS REST service
(e.g. a Kubernetes Service DNS name / ClusterIP), and set the base64 value to
`base64("onos:rocks")` = `b25vczpyb2Nrcw==`.

```nginx
# Proxy the RSA read-only REST surface to ONOS, terminating CORS at the edge.
location /onos/rsa/ {
    # (a) Answer the browser CORS preflight here; ONOS never sees it.
    if ($request_method = OPTIONS) {
        add_header Access-Control-Allow-Origin  "$http_origin" always;
        add_header Access-Control-Allow-Methods "GET, POST, DELETE, OPTIONS" always;
        add_header Access-Control-Allow-Headers "Content-Type, Authorization" always;
        add_header Access-Control-Max-Age       86400 always;
        add_header Content-Length 0;
        add_header Content-Type   text/plain;
        return 204;
    }

    # (b) Forward everything else to ONOS with Basic auth injected server-side.
    #     base64("onos:rocks") = b25vczpyb2Nrcw==
    proxy_set_header Authorization "Basic b25vczpyb2Nrcw==";
    proxy_set_header Host $host;
    proxy_pass http://ONOS_HOST:8181;

    # (c) Make sure the CORS origin header is present on proxied responses too.
    #     (ONOS's RsaCorsFilter already adds Access-Control-Allow-Origin: *;
    #      this line guarantees it even if that ever changes.)
    add_header Access-Control-Allow-Origin "$http_origin" always;
}
```

Notes:

- Echoing `$http_origin` back (instead of `*`) is required if you ever need to
  send credentials from the browser (`Access-Control-Allow-Credentials`); for a
  read-only public surface, a literal `*` is also fine.
- The `Authorization` header is injected by nginx so the ONOS Basic-auth
  credentials stay server-side and never reach the browser.
- The same block works for the whole `/onos/` surface if you proxy more than the
  RSA endpoints; scope it to `/onos/rsa/` to expose only the read-only API.
