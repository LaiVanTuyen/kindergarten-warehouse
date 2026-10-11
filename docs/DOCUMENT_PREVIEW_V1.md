# Document preview V1 — 2026-10-11

This slice implements an independent, permission-checked reader for a single school's
deployment. It does **not** close the draft/rating/copyright/reporting backlog or certify
the entire application for production.

## Contract

| Route | Response | Access |
| --- | --- | --- |
| `GET /api/v1/resources/{id}/preview` | `ApiResponse<PreviewResponse>` | `ResourceAccessGuard.requireViewable` |
| `GET /api/v1/resources/{id}/preview/pages/{page}` | JPEG, `Cache-Control: no-store`, `Vary: Cookie, Authorization` | Same guard, checked on **every** request |
| `GET /api/v1/resources/{id}/file` | Original attachment | Existing download guard, login required |

`PreviewResponse`: `status`, `pageCount`, `totalPages`, `truncated`, `message`.
Statuses: `PROCESSING`, `READY`, `FAILED`, `UNSUPPORTED`, `BUSY`.
Pages are one-based. Unknown/out-of-range/not-ready pages return 404. Visibility is
checked before archive status, preserving 404/410 order. DRAFT is never previewable.
Owner/admin can preview other viewable non-public workflow states as the existing guard
allows. Guest can preview only content it is permitted to view; no original URL or
storage key is exposed. Rendering/public reading does not increment download counters.
Authenticated video playback still uses the existing original-file route, not this
document-preview pipeline, and retains that route's accounting behavior.

## Generation and resource bounds

- Metadata lookup lazily starts generation. Repeated reads deduplicate the same job.
- One converter thread, queue capacity 8. No conversion in the upload HTTP transaction.
- Private MinIO original -> temporary input -> LibreOffice PDF for Office documents ->
  PDFBox raster pages. Images are re-encoded as JPEG, not forwarded as originals.
- Supported document extensions: PDF, DOC/DOCX, PPT/PPTX, XLS/XLSX; image extensions:
  PNG, JPG/JPEG, WebP (TwelveMonkeys ImageIO decoder).
- Source and generated PDF maximum 50 MiB. Total generated page images maximum 50 MiB.
  At most 100 pages and longest rendered edge 1600 px. Image input maximum 40M pixels.
- LibreOffice subprocess: 60-second timeout, isolated temporary profile, high macro
  security, application credential environment removed. It is terminated on timeout.
- PDF rendering checks a 90-second deadline **between pages**. This is not a hard
  interruption of a single pathological PDF operation.
- Terminal failures cached for 120 seconds; subsequent metadata lookups can retry.
- FE polls every 2.5 seconds without aborting slow in-flight requests; 10-second HTTP
  timeout; stops on terminal states or after about five minutes; destroys subscriptions
  on navigation away. Manual retry remains available.

## Persistence and deployment

`APP_PREVIEW_CACHE_DIR=/var/cache/warehouse-previews` and the private `preview_data`
Compose volume preserve completed manifests/pages across container restarts. Default
for running Java outside Compose is `/tmp/warehouse-previews`.

Cache keys are SHA-256 of pipeline version, resource ID and original file URL. File
replacement immediately selects a new key, so an old preview cannot be served for the
new file. Permission changes do not require cache invalidation because access checks
are never cached. Incomplete jobs are safely regenerated on the next read after restart.

Cache maintenance runs before new conversions: seven-day expiry and soft 512 MiB budget,
skipping queued/running jobs. Eviction removes only generated hash directories, not
MinIO originals. A conversion may temporarily exceed the soft budget by one job.
Keep the cache private; never mount it as an nginx static/public directory.

Both Dockerfiles now include LibreOffice and Vietnamese-capable fonts. Build:

```powershell
docker compose up -d --build app
```

When a tested Maven package already exists, the local shortcut is:

```powershell
docker build -f docker/local-runtime.Dockerfile -t kindergarten-warehouse-app .
docker compose up -d --no-deps --no-build app
```

The production Compose cache configuration is prepared, **not deployed or runtime
verified on a VPS**. Production must run the newly built image, not an older registry tag.

## Verification

- `mvn package`: 160 tests pass, including real SecurityFilterChain matcher tests,
  denied metadata/direct-page access, private/internal visibility, archived/deleted/draft,
  deduplication, persistence, replacement invalidation, failure cache and actual rendering.
- MySQL 8.0.46 Testcontainers: existing 20 visibility/favorites IT pass. These do not
  replace the HTTP converter acceptance below.
- Portal Jest: 19 tests pass. Includes processing -> ready, page/zoom bounds, 404,
  slow request handling, five-minute stop, route change and destroy cancellation.
- Portal/admin demo builds and Portal production build succeed; existing bundle budget
  warnings remain. No new converter load test was performed.
- Real local HTTP acceptance: PDF 3 pages, DOCX 3, PPTX 2, XLSX 1. Guest page image 200,
  original 401, private metadata/page 404, authorized admin page 200, download counter
  unchanged. Six synthetic resources were created in local storage during acceptance;
  they are visibly labelled `[DEMO]`, not real school records.
- Existing local PNG resource rendered successfully in the browser. WebP decoder is
  registered by a test; complex WebP documents and legacy DOC/PPT/XLS are not part of
  the real HTTP fixture set yet.

Reproduce synthetic fixtures using `docs/fixtures/*.fodt/*.fodp/*.csv`, convert them
to DOCX/PPTX/XLSX with the runtime image, and run `scripts/test-preview.ps1` with
PowerShell 7. The PDF fixture is generated by `PreviewRendererTest` in `target/`.
The script reads admin credentials from local `.env` without printing them and creates
four additional labelled records per run; `-ReuseExisting` repeats acceptance against
the four latest matching labelled fixtures without creating more records. Do not run
it against production.

Demo:

- Word: http://localhost:4201/resources/demo-hoc-qua-tro-choi--ban-docx-1791696032040
- PDF: http://localhost:4201/resources/demo-hoc-qua-tro-choi--ban-pdf-1791696014089
- Slides: http://localhost:4201/resources/demo-hoc-qua-tro-choi--ban-pptx-1791696049802

## Limits before production expansion

1. This is a single-instance local/private-school converter, not a distributed durable
   job queue. Multiple app replicas require a separate worker, shared object cache,
   job leases/locking and coordinated eviction.
2. Office conversion is a subprocess, but PDF parsing still runs in the application JVM.
   Macro settings and bounded queues are **not a security sandbox**. For untrusted public
   uploads or significant load, isolate conversion in a non-root worker with no network,
   no application credentials, hard CPU/RAM/time limits and an updated parser image.
3. Fonts/layout may differ from Microsoft Office. Review representative school material,
   large slides and spreadsheet print areas before acceptance.
4. Raster preview is not DRM: a viewer can save screenshots/page images. It preserves the
   original-download access rule, not a promise that visible content cannot be copied.
5. No OCR, full-text indexing, annotations, slide animations, audio preview, or guest
   uploaded-video transcoding. YouTube remains the existing external player.

Primary implementation references:
[PDFBox](https://pdfbox.apache.org/download.cgi),
[LibreOffice CLI](https://help.libreoffice.org/latest/en-US/text/shared/guide/start_parameters.html),
[TwelveMonkeys](https://github.com/haraldk/TwelveMonkeys/releases).
