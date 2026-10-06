# Active shelf frame performance V64

## User-visible gap

`ShelfLibraryV5` delegates to `ShelfLuoShuFunctionalV1`, so its three-item floating dock is the
current stable shelf navigation rather than an archived prototype. The selection pill used
`animateDpAsState` and read its changing value through the direct `Modifier.offset(x, y)` overload.
That read happened during composition on every animation frame and could recompose the complete
dock while moving between Shelf, Bookstore, and Profile. The same active shell kept its shelf
revision counter in generic boxed state even though it is always a non-null `Int`.

## Independent change

- The pill retains the same spring, position formula, 5dp vertical offset, 64dp cap, touch targets,
  colors, and rounded shape, but now reads `pillX` inside lambda `Modifier.offset`. Only placement
  and drawing are invalidated while the pill moves; the three navigation items do not need to be
  recomposed for each intermediate position.
- `shelfRevision` now uses `mutableIntStateOf`. Its initial value, increments, `remember` keys,
  custom-shelf refresh behavior, and saved-state restoration are unchanged.
- `ActiveShelfFramePerformanceV64Test` prevents either active path from returning to composition-
  phase animated placement or boxed integer state.

Android's Compose documentation recommends lambda modifiers for frequently changing state so the
read happens in a later phase, and documents the direct and lambda offset overloads separately:

- <https://developer.android.com/develop/ui/compose/performance/phases>
- <https://developer.android.com/develop/ui/compose/performance/modifier-phases>
- <https://developer.android.com/reference/kotlin/androidx/compose/runtime/MutableIntState>

The pinned Legado `3a7c4daaf79c652b8e60d6aad80f665bbf6cacba` (GPL-3.0) and Readium
`1b1f6b308a7b6f968b2bf1e66c84912879466f75` (BSD-3-Clause) revisions remain behavior and lifecycle
references only. No code was copied from either project, and no reader, book-source, storage,
WebView, native bridge, or database behavior changed.

## Acceptance boundary

This commit requires fresh unfiltered Reader, Browser, and JVM reports, Debug build, lint, and both
real seed / force-stop / restore process checks. The preceding 148 Reader, 16 Browser, and 717 JVM
results are baselines only, not acceptance evidence for this change. Reading position, original
EPUB bytes and artwork, script safety, bookmarks, draft cancellation, database transactions, and
independent-copy assertions remain mandatory. Real AI services, third-party login or CAPTCHA,
physical devices, API 28 renderer reclamation, and real split-screen/freeform windows remain outside
the authorized environment.
