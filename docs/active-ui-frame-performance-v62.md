# Active UI frame performance V62

## User-visible gap

The active V3 dock read its animated horizontal offset during composition. The active V30
scroll reader also read the full `LazyListState.layoutInfo` during composition even though its
fixed chrome only needs to change when the first visible page changes. Both values can update on
every animation or scroll frame, needlessly recomposing larger UI subtrees. The TTS rate used a
boxed generic state despite being a primitive float.

## Independent implementation

- The dock now uses the lambda `Modifier.offset` overload, so the animated value is read during
  placement instead of composition.
- The scroll reader wraps its visible chapter/page pair in `derivedStateOf`. Pixel-level list
  changes no longer invalidate the chrome unless that pair changes. Existing fallback chapter and
  page values, key parsing, drawing, persistence and page-location behavior are unchanged.
- The TTS rate uses `mutableFloatStateOf`, preserving its value and persistence keys without
  boxing each state update.

Android's Compose performance documentation recommends lambda modifiers for rapidly changing
offsets and `derivedStateOf` when scroll input changes more often than the UI result needs to:

- <https://developer.android.com/develop/ui/compose/performance>
- <https://developer.android.com/develop/ui/compose/performance/phases>
- <https://developer.android.com/develop/ui/compose/side-effects#derivedstateof>

The pinned Readium `1b1f6b308a7b6f968b2bf1e66c84912879466f75` (BSD-3-Clause) and Legado
`3a7c4daaf79c652b8e60d6aad80f665bbf6cacba` (GPL-3.0) references were retained for lifecycle
and reader comparison only. No code was copied from either project, and no WebView, book-source,
storage or native bridge behavior changed in this batch.

## Acceptance boundary

The new JVM contract locks the three phase/state choices. Full unfiltered Reader, Browser and JVM
suites, Debug build, lint, and both real force-stop process restorations must pass on this commit.
Expected counts are Reader 148, Browser 16 and JVM 716; these are not results until their raw
reports are archived and checked. Existing reading-position, original-file, script, illustration,
bookmark, draft-cancellation, database-transaction and independent-copy assertions remain intact.
