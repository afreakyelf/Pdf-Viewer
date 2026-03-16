# CHANGELOG

# [v2.4.0] — 2026-03-16

This release aligns the three cache strategies with their intended behavior and adds broad regression coverage around caching, rendering, and embedded-view lifecycle cleanup.

### Cache Strategy Alignment

The `CacheStrategy` enum behavior is now clearly defined and consistently enforced:

| Strategy | Remote PDF reuse | Page bitmaps in memory | Page bitmaps on disk | Prefetch |
|---|---|---|---|---|
| `MAXIMIZE_PERFORMANCE` | ✅ Reuse persistent cache | ✅ Yes | ✅ Yes | ✅ Enabled |
| `MINIMIZE_CACHE` | ✅ Reuse current session | ✅ Yes | ❌ No | ✅ Enabled |
| `DISABLE_CACHE` | ❌ Transient only | ✅ Yes (visible only) | ❌ No | ❌ Disabled |

### Bug Fixes
- **Incorrect remote cache retention across strategies:** `MAXIMIZE_PERFORMANCE` now correctly persists and reuses remote PDFs across sessions; `DISABLE_CACHE` now operates transiently without polluting or deleting the persistent cache used by other strategies.
- **Stale disk page-bitmap artifacts on strategy downgrade:** Reopening a document under a stricter strategy (e.g. switching from `MAXIMIZE_PERFORMANCE` to `MINIMIZE_CACHE`) no longer serves stale page bitmaps from a previous higher-retention session.
- **`PdfRendererView.initWithUrl` transient file not cleaned up:** When `PdfRendererView` is embedded directly (outside `PdfViewerActivity`) and `DISABLE_CACHE` is used, the transient remote file is now deleted on lifecycle destroy. Previously, cleanup only happened inside `PdfViewerActivity`.
- **DISABLE_CACHE blank pages** ([#222](https://github.com/afreakyelf/Pdf-Viewer/issues/222)): Pages no longer render blank when `DISABLE_CACHE` is set; the cache-miss path correctly triggers a fresh render every time.
- **Fast-scroll bitmap corruption** ([#223](https://github.com/afreakyelf/Pdf-Viewer/issues/223)): Fixed visual corruption caused by bitmap reuse during rapid scrolling; each render job now operates on a dedicated bitmap.
- **Render job and callback bookkeeping hardened:** `PdfRendererCore` render-job tracking and completion callbacks are now guarded with minSdk-21-safe logic, preventing stale callbacks from racing with new render requests.

### Internal / Testing
- Introduced a dedicated internal `CachePolicy` abstraction to centralize and express per-strategy behavior, replacing scattered conditionals.
- Added unit tests covering: cache policy mapping, document retention rules, and transient cleanup behavior.
- Added hermetic instrumented tests covering: local PDF behavior under all three strategies, remote PDF reuse vs. non-reuse, embedded `PdfRendererView` lifecycle cleanup, and strategy-downgrade stale-bitmap behavior.
- Added a debug-only embedded host activity to verify `PdfRendererView` cleanup outside `PdfViewerActivity`.
- GitHub Actions publishing workflow updated to use newer `actions/checkout` and `actions/setup-java` versions, with tag-based dynamic version resolution.
- Library version is now resolved at publish time from the Git tag (`PUBLISH_VERSION` Gradle property), enabling fully automated Maven Central releases.

### Compatibility
- No public API breaking changes. Existing `CacheStrategy` APIs are unchanged; behavior is now more consistent and predictable.

---

# [v2.3.9] — 2026-03-16

### Bug Fixes
- **DISABLE_CACHE blank pages:** Pages no longer render blank when `DISABLE_CACHE` is set; the cache-miss path now correctly triggers a fresh render every time ([#222](https://github.com/afreakyelf/Pdf-Viewer/issues/222)).
- **Fast-scroll bitmap corruption:** Fixed visual corruption caused by bitmap reuse during rapid scrolling; each render job now operates on its own dedicated bitmap ([#223](https://github.com/afreakyelf/Pdf-Viewer/issues/223)).

### Reverts
- Reverted "Fix PDF quality degradation on zoom (#247)" due to instability introduced by size-aware cache keys.

### Internal / Build
- Added regression tests covering DISABLE_CACHE and fast-scroll cache paths.
- GitHub Actions publishing workflow updated to use newer `actions/checkout` and `actions/setup-java` versions, with tag-based dynamic version resolution.
- Library version is now resolved at publish time from the Git tag (`PUBLISH_VERSION` Gradle property), enabling fully automated Maven Central releases.

---

# [v2.3.8] — 2026-03-15

### New Features
- **Zoom controls exposed** (`#246`): `PdfRendererView` and `PdfRendererViewCompose` now expose `zoomIn()`, `zoomOut()`, and `zoomTo(scale)` methods. A Jetpack Compose example using FABs for zoom in/out has been added to the sample app.
- **Configurable max zoom** (`#239`): Maximum pinch-zoom scale is now configurable via the `pdfView_maxZoom` XML attribute or programmatically via `setMaxZoom(scale)`. Default remains `5×`.
- **Page navigation helpers** (`#250`): `scrollToNextPage()` and `scrollToPreviousPage()` respect the current zoom level and scroll within a tall zoomed page before advancing to the next/previous one, preventing jarring jumps in landscape or high-zoom states.
- **Zoom-settled listener alias**: `onZoomSettled` callback added as a stable alias for the zoom-end event so callers do not need to parse raw scale values.

### Bug Fixes
- **API 33 `getParcelableExtra` NPE** (`#244`, `#245`): Replaced deprecated `getParcelableExtra` call with the type-safe `getParcelableExtra(name, Class)` overload to prevent a `NullPointerException` on Android 13+.
- **ViewHolder height not reset on rebind** (`#242`): Page view height is now reset to `WRAP_CONTENT` in `onBindViewHolder`, preventing stale height values from a previous page persisting during fast scroll.
- **PdfRenderer shutdown race** (`#241`): `PdfRendererCore.closePdfRenderProxy()` is now guarded so concurrent close calls cannot cause a `NativeException` from double-close on the native `PdfRenderer`.
- **Scroll state not restored after re-init** (`#225`): `PdfRendererView` saves and restores the visible page index across `initWith*` re-calls (e.g., after a rotation or back-stack re-attach), so users no longer land back on page 1.
- **Zoomed-PDF scrolling regression** (`#230`): `smoothScrollBy` delta is now divided by the current zoom scale and clamped to the remaining distance to the page edge, fixing over-scroll and under-scroll at high zoom levels.
- **Bottom sheet PDF scrolling** (`#231`): `PdfRendererView` now sets `nestedScrollingEnabled = true` so it participates in the nested-scroll protocol; fixes the PDF being unscrollable inside `BottomSheetDialogFragment` and `BottomSheet` Compose containers.
- **Compose bottom sheet scroll interop** (`#231`): `PdfRendererViewCompose` wraps the view in a `rememberNestedScrollInteropConnection()` modifier, forwarding unhandled scroll events to the Compose bottom-sheet host.
- **ViewPager tab reattach** (`#235`): Fixed a regression where switching back to a tab containing a `PdfRendererView` after it had been detached would leave the view in a blank state; the adapter now re-binds correctly on reattach.
- **Cache key collision for same-named PDFs** (`#249`): `CacheHelper.getCacheKey` now hashes the full source path (URL or file absolute path) with SHA-256 instead of using `file.name`, eliminating collisions between identically-named PDFs in different directories.
- **`ParcelFileDescriptor` and `PdfRenderer` leaks on init failure** (`#249`): `PdfRendererCore.create()` now closes the native `PdfRenderer` if post-init setup fails, and the caller closes the `ParcelFileDescriptor`; both resources are fully guarded via `try/catch` in all `initWith*` paths.
- **`initWithUri` null file descriptor** (`#249`): A `null` result from `ContentResolver.openFileDescriptor` now routes to `onError` instead of silently returning, ensuring callers always receive failure feedback.

### Java Compatibility Fix
- **`NoClassDefFoundError` for `lifecycle-process` in Java consumer apps** (`#248`): Added a `constraints` block in `pdfViewer/build.gradle.kts` to pin `lifecycle-process` and `lifecycle-runtime-ktx` to `≥ 2.8.7`, preventing older versions from being resolved when an app's transitive dependency tree downgrades the lifecycle libraries. The library's consumer ProGuard rules now include a `keep` rule for the anonymous `LifecycleObserver` classes generated by the Kotlin compiler.

### Internal / Build
- Kotlin standard library updated to 2.1.20.

---

# [v2.0.0]

- **Orientation Change Handling:** Improved to maintain the current page position during device orientation changes.
- **Security Enhancement for File Paths:** Implemented secure file path handling to prevent directory traversal attacks.
- **Efficient Caching Strategy:** Optimized to store only the most recent PDF file in the cache, reducing storage usage.
- **Screenshot Prevention Feature:** Added functionality to disable screenshots and screen recordings for enhanced privacy.
- **Dynamic UI Customization:** Introduced flexibility in customizing the UI elements programmatically based on XML attributes.
- **Compatibility with 'NoActionBar' Theme:** Ensured default values for missing attributes, enabling smooth integration with various themes.
- **Jetpack Compose Support:** Added a composable function PdfRendererViewCompose for Jetpack Compose applications.
- **Performance Optimization:** Enhanced performance for better handling of large PDF files.

These updates focus on improving the user experience, security, and overall performance of the library.

# [v1.1.0]
- Added `pdfView_page_margin` as an attribute to change spacing (Right, Left, Top, Bottom) from the pages Or use the following attribute to add space for each edges:
```xml
<com.rajat.pdfviewer.PdfRendererView
    android:id="@+id/pdfView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:pdfView_divider="@drawable/divider"
    app:pdfView_engine="internal"
    app:pdfView_enableLoadingForPages="true"
    app:pdfView_page_marginRight="10dp"
    app:pdfView_page_marginLeft="10dp"
    app:pdfView_page_marginTop="5dp"
    app:pdfView_page_marginBottom="5dp"
/> 
```

- Added loading view for each pages. It's available by using `pdfView_enableLoadingForPages` attribute.
- Optimise rendering PDF process by using `CoroutineScope` instead of `GlobalScope`

## Breaking Changes
- Added night/light mode background for `PdfViewerActivity`. If you enable spacing it would show background of this activity.
- Remove background from the list of pages (`PinchZoomRecyclerView`) to let developers to add custom background for PDF viewer. It may affect to see the PDF viewer result by showing the parent background.