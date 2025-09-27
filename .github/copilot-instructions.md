# Copilot Code Review Instructions — Pdf-Viewer

Use this guide to review all PRs across the library (PdfRendererCore, PdfRendererView, PdfViewAdapter, PdfDownloader, PinchZoomRecyclerView, PdfViewerActivity, Compose interop).

## Review Style
- Be concise, constructive, and specific: reference exact files/symbols.
- Lead with positives; group feedback into **Must fix**, **Should fix**, **Nice to have**.
- Provide code snippets for critical fixes when feasible.

---

## Architecture & Boundaries
- Keep responsibilities separated:
  - **PdfRendererCore**: rendering, page cache, prefetch, threading.
  - **PdfRendererView / Compose**: UI glue, state, listeners, no heavy work.
  - **PdfViewAdapter**: binding pages to RV items, diffing, placeholders.
  - **PinchZoomRecyclerView**: zoom/pan/scroll coordination.
  - **PdfDownloader**: network & storage only.
- Avoid cross-layer knowledge leaks (e.g., UI classes touching renderer internals).
- Preserve backward compatibility; if changing APIs, use `@Deprecated(message, ReplaceWith(...))`.

---

## Concurrency, Lifecycles, & Scopes
- No raw `Thread`/`Executor`; prefer coroutines.
- Tie work to the right scope:
  - Views/Activities: `lifecycleOwner.lifecycle.coroutineScope`.
  - Core/background: structured concurrency with parent scope injection.
- Ensure cancellation in `onDestroy`/`onDetachedFromWindow` and when RV viewholders are recycled.
- UI callbacks always hop to `Dispatchers.Main`.

---

## Resource Safety
- Always close: `PdfRenderer`, `ParcelFileDescriptor`, OkHttp `Response`, `InputStream`/`OutputStream`.
- Use `use {}` or `try/finally` for every resource path (success, error, cancel).
- Prevent bitmap leaks: recycle or let LRU evict; never hold raw `Bitmap` beyond cache or binding.

---

## Rendering, Caching, Prefetch
- Rendering off main thread; propagate results to main for UI.
- LRU cache size tuned to device memory class; document rationale.
- Prefetch window reasonable (e.g., ±2–3 pages) and adaptive to zoom/velocity.
- Handle scale changes: invalidate tiles/bitmaps appropriately.
- Fallbacks for sporadic render misses: bounded retry with backoff, guard against infinite loops.

---

## RecyclerView & Gestures (Pinch/Zoom/Scroll)
- Zoom centered at focal point; maintain content position on scale.
- Scrollbars reflect zoomed content extents.
- No nested scroll conflicts; test both LTR/RTL.
- ViewHolder reuse safe: clear old bitmaps, cancel pending jobs on `onViewRecycled`.

---

## Compose Interop
- State hoisting: `PdfRendererViewCompose` exposes state/events; no blocking work in composables.
- Use `LaunchedEffect`/`DisposableEffect` for lifecycle tie-ins.
- Remembered objects (`remember { ... }`) not leaking Activity/Context.

---

## Networking & File I/O
- One reusable `OkHttpClient` (connection pooling, DNS, dispatcher).
- Timeouts sensible; follow redirects if required; validate `Content-Type`/length when available.
- **Atomic writes**: stream to a temporary file and `renameTo` on success so cancellations/errors don’t leave partial PDFs.
- Progress callbacks are throttled (time-based) to avoid main-thread spam.

---

## Error Handling & Logging
- Clear error taxonomy (e.g., `DownloadFailedException`, `RenderFailedException`).
- Surface errors to `PdfViewStatusListener.onError` / Compose state.
- Use library logger with levels; avoid `Log.*` directly and avoid sensitive data.
- Provide actionable messages (status code, hint, operation).

---

## Performance
- Avoid large allocations in hot paths; reuse buffers.
- Bitmap configs appropriate (e.g., `ARGB_8888` vs `RGB_565` trade-offs).
- Avoid unnecessary copies (prefer `copyTo` streams with buffer).
- Measure jank risks: progress floods, layout thrash, allocation spikes.

---

## Accessibility & UX
- Touch targets, content descriptions, keyboard navigation (arrow/tab) where applicable.
- Respect system font scaling; test at 1.3×/2.0×.
- Announce loading/progress/accessibility events appropriately.

---

## Security & File/URI Handling
- Support `FLAG_SECURE` opt-in; don’t bypass if set.
- Handle SAF/`content://` URIs correctly; no assuming file paths.
- Don’t log URLs/headers unless debug-only and scrubbed.

---

## Testing & Tooling
- Unit/instrumented tests for:
  - Rendering success/failure paths
  - Cancellation & lifecycle dispose
  - Downloader: success/error/cancel (MockWebServer)
  - RV recycling and zoom edge cases
- Gradle Managed Devices matrix (API 27/30/35) green.
- Benchmarks or micro-tests for render throughput when changed.

---

## Documentation & Samples
- Update KDoc for new APIs; include usage caveats (threading, lifecycle).
- Sample app reflects best practices (no shortcuts hidden in demo).
- Changelog entry for user-visible changes; migration notes if behavior changes.

---

## Reviewer Checklist
- [ ] Correct module boundaries; no UI-work in core, no core-leaks in UI
- [ ] Coroutines used; lifecycle-bound; cancellation wired
- [ ] All resources closed with `use {}` or `finally`
- [ ] Rendering off main; cache/prefetch reasonable; retries bounded
- [ ] RV/zoom behaviors correct; recycled views cancel work
- [ ] Compose code side-effect safe; no leaks
- [ ] Single OkHttp client; **atomic file writes**; throttled progress
- [ ] Errors typed & surfaced; logs through library logger; no sensitive data
- [ ] Performance considered; no obvious jank/OOM risks
- [ ] Tests updated/added; CI matrix passes
- [ ] Docs/KDoc/CHANGELOG updated; samples reflect changes

---

## Comment Templates

**Positive intro**
> Great improvement! 🙌 This change aligns with the architecture and improves robustness/perf in **{area}**.

**Must fix**
> We need to ensure **{resource}** is closed in all paths. Wrap with `use {}` or `finally` to avoid leaks.

**Should fix**
> Tie this work to a lifecycle-bound scope and cancel in `onDestroy`/`onViewRecycled` to prevent stray jobs.

**Nice to have**
> Consider throttling progress emissions (e.g., every 100–150ms) to reduce main-thread load on large files.

