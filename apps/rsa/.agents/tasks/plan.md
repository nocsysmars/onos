# Implementation Plan — RSA read-only REST API (backend only)

Goal: add read-only GET endpoints to the RSA ONOS app so an external web UI (via nginx reading ONOS REST) can visualize topology routing, spectrum occupancy, live lightpaths, and KPIs. Backend only. Purely additive — existing `POST/GET/DELETE /onos/rsa/path[/{id}]`, `assign/release/get`, `SpectrumTable` methods, and the null-provider vs real-OCh intent paths stay unchanged.

Decisions (grounded in the code read):
- CORS is a single JAX-RS `@Provider ContainerResponseFilter` registered only in `RsaWebApplication.getClasses()`. ONOS scopes `getClasses()` per web app (see `CoreWebApplication` registering `AuditFilter`), so this affects `/onos/rsa` only — chosen over per-resource headers because it is DRY and cannot leak into other REST contexts.
- Spectrum data is exported through a new immutable `SpectrumTable.snapshot()` rather than by exposing the internal maps, preserving encapsulation and the class's `synchronized` discipline.
- Shared `RsaResult` JSON is factored into one helper so `/paths` matches single-result `encodeResult` exactly (no field drift).
- Build/test: this app has no unit tests; the authoritative verification is a clean `.oar` build plus live curl against the running `onos-mno-27`. Every build must pass `--host_force_python=PY3` (`.bazelrc` defaults to PY2) with `JAVA_HOME` = OpenJDK 11, bazelisk pinned to 3.7.2.

Build command (reused in every verify step):
`cd /home/manager/build/onos-2.7.0 && JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 bazelisk build --host_force_python=PY3 //apps/rsa:onos-apps-rsa-oar`
oar output: `/home/manager/build/onos-2.7.0/bazel-bin/apps/rsa/onos-apps-rsa-oar.oar`

- [ ] 1. Add an immutable spectrum snapshot export to `SpectrumTable` without touching existing methods.
      Add value classes `LinkSpectrum {ConnectPoint src, dst; int slotCount; List<Integer> occupiedSlots; int freeSlotCount; int maxContiguousFree; List<ReservationView> reservations}`, `ReservationView {String requestId; int startSlot; int width}`, `SpectrumSnapshot {int slotCount; List<LinkSpectrum> links}`, and `public synchronized SpectrumSnapshot snapshot()` built from `occupancy` + `reservations`. Leave `DEFAULT_SLOT_COUNT/slotCount/isFree/occupy/release/contains/undirected/bits/Reservation` unchanged.
      Files: apps/rsa/src/main/java/org/onosproject/rsa/SpectrumTable.java
      Verify: run the build command above — it compiles.

- [ ] 2. Add read accessors to the service layer.
      In `RsaService` add `Collection<RsaResult> listActive()`, `int slotCount()`, `SpectrumTable.SpectrumSnapshot spectrumSnapshot()`, `long blockedCount()` (javadoc). In `RsaManager` implement them: `listActive()` = `ImmutableList.copyOf(results.values())`; `slotCount()`/`spectrumSnapshot()` delegate to the table; add `AtomicLong blockedCounter`, increment at each of the three `RsaResult.blocked(...)` return sites in `assign`, expose `blockedCount()`. Do not change routing/first-fit/intent logic.
      Files: apps/rsa/src/main/java/org/onosproject/rsa/RsaService.java, apps/rsa/src/main/java/org/onosproject/rsa/RsaManager.java
      Verify: run the build command — it compiles.

- [ ] 3. Factor the shared `RsaResult` JSON encoder.
      Move `encodeResult` into package-private `org.onosproject.rsa.web.RsaJson.encodeResult(ObjectMapper, RsaResult)` keeping identical fields/order (requestId, blocked=false, src, dst, startSlot, width, devices[], links[], ochSignal via `OchSignalCodec.encode`); have `RsaPathWebResource` delegate to it. No HTTP behavior change.
      Files: apps/rsa/src/main/java/org/onosproject/rsa/web/RsaJson.java (new), apps/rsa/src/main/java/org/onosproject/rsa/web/RsaPathWebResource.java
      Verify: run the build command — it compiles.

- [ ] 4. Add the four read-only resources.
      `RsaPathsWebResource` `@Path("paths")` → `{"paths":[encodeResult...]}` from `listActive()`. `RsaSpectrumWebResource` `@Path("spectrum")` → `{slotCount, links:[{src,dst,occupiedSlots,freeSlotCount,maxContiguousFree,reservations:[{requestId,startSlot,width}]}]}` from `spectrumSnapshot()`. `RsaTopologyWebResource` `@Path("topology")` → `{nodes:[{id,name(AnnotationKeys.NAME fallback id),type}], links:[{src,dst,capacity=slotCount,used,utilization}]}` from `DeviceService.getDevices()` + `LinkService.getActiveLinks()` overlaid with the snapshot. `RsaStatsWebResource` `@Path("stats")` → `{totalLinks,totalSlots,usedSlots,overallUtilization,activeRequests,blockedCount,maxContiguousFree,averageFragmentation}`. All camelCase, extend `AbstractWebResource`.
      Files: apps/rsa/src/main/java/org/onosproject/rsa/web/RsaPathsWebResource.java, RsaSpectrumWebResource.java, RsaTopologyWebResource.java, RsaStatsWebResource.java (all new)
      Verify: run the build command — it compiles.

- [ ] 5. Add the CORS filter and register everything.
      `RsaCorsFilter implements ContainerResponseFilter` `@Provider`, stamping `Access-Control-Allow-Origin: *`, `Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS`, `Access-Control-Allow-Headers: Content-Type, Authorization`. Register all new classes + the filter in `RsaWebApplication.getClasses(...)`. If live preflight returns 405, add `RsaCorsPreflightResource` `@Path("{path:.*}") @OPTIONS` returning `Response.ok()` and register it too.
      Files: apps/rsa/src/main/java/org/onosproject/rsa/web/RsaCorsFilter.java (new), apps/rsa/src/main/java/org/onosproject/rsa/web/RsaWebApplication.java
      Verify: run the build command — it compiles.

- [ ] 6. Confirm BUILD deps.
      `DeviceService/LinkService/ConnectPoint/AnnotationKeys/OchSignalCodec`/JAX-RS filter interfaces are already on `CORE_DEPS + REST + optical-model`; add a dep only if a compile error proves one missing.
      Files: apps/rsa/BUILD (only if needed)
      Verify: run the build command — it compiles and `bazel-bin/apps/rsa/onos-apps-rsa-oar.oar` exists.

- [ ] 7. Hotswap into the live controller and curl-test every endpoint.
      Because the app is already ACTIVE, force-reinstall: `cp` the oar into the `onos-mno-27` pod (namespace `ai-optical`), DELETE `/onos/v1/applications/org.onosproject.rsa`, re-POST `--data-binary @.../onos-apps-rsa.oar '…/applications?activate=true'`, poll until `state=ACTIVE`. Then from inside the pod `curl -u onos:rocks http://localhost:8181/onos/rsa/{paths,spectrum,topology,stats}` (all 200), confirm legacy `path/{id}` POST/GET/DELETE still 201/200/204, and check `Access-Control-Allow-Origin` on a cross-origin GET plus an OPTIONS preflight. Leave no leftover test requests/intents. (Service is ClusterIP-only; reuse patterns from `/home/manager/ai-optical-poc/80-rsa-real/install-rsa-app.sh` and `run-demo-rsa-real.sh`.)
      Files: none (verification only)
      Verify: each endpoint returns HTTP 200; CORS header present; legacy endpoints unchanged.

- [ ] 8. Commit and push to GitHub.
      Stage ONLY the actual source/BUILD files BY NAME (never `git add -A`/`.` — the worktree holds uncommitted `.agents/` artifacts and POC files). Commit on a new branch and push to remote `nocsysmars`. Do not commit `.agents/` artifacts or any `bazel-*` output.
      Files: git only
      Verify: `git status` shows only intended files committed; `git push` to `nocsysmars` succeeds.

Assumptions / gaps:
- `/onos/rsa/topology` includes all devices (not only ROADMs); switch to optical-only if the UI needs it.
- `blockedCount` is an in-process counter reset on app reinstall (no historical persistence) — documented, matches PoC scope.
- CORS `Allow-Origin: *` suits a PoC behind nginx; tighten to a specific origin constant if required.
