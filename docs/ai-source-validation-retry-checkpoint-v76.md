# AI source validation retry checkpoint V76

## User-visible gap

V67 kept the failed attempt's ordinary/browser transport as the primary retry, but every retry
constructed a fresh builder. If search rules had already passed against the live page and the later
catalogue or content stage failed, the same earlier rules were sent to the model again. This repeated
latency and could repeat provider charges even though the app already had stronger evidence: a
successful extraction from the real page.

## Change

- `AiValidationCheckpointV76` retains only rules that completed live extraction validation. It is an
  in-memory checkpoint for the current ViewModel retry chain, not a persisted source and not a claim
  that the website cannot change.
- A same-site, same-book-name, same-transport retry reuses those rules but fetches the homepage,
  search page, book page and chapter page again and runs the normal safety and completeness checks.
- If a cached rule no longer extracts valid data, that stage and all later checkpoints are discarded;
  the existing bounded correction path is used. Network failures do not turn into selector repair or
  another automatic request loop.
- Changing ordinary/browser mode or the inputs starts a fresh checkpoint. Explicit stop, leaving an
  active generation, successful completion and explicit regeneration clear it.
- The terminal state exposes `aiCanResumeValidatedRules`; the same-mode primary action becomes
  **保留已通过规则重试（普通/浏览器）**. The alternative transport remains available and never shares
  rules across transport boundaries.

The generation token, cancellation behavior, two-call-per-stage correction budget, unsupported-rule
rejection, source storage transaction, saved-source identity, browser profile and session boundaries
are unchanged.

## References and adoption boundary

- [Android UI state production](https://developer.android.com/topic/architecture/ui-layer/state-production)
  treats user events as inputs to a state-production pipeline. V76 therefore publishes retry
  capability as UI state and keeps the retry event in the ViewModel rather than hiding it in a
  composable-local flag.
- [Android UI events](https://developer.android.com/topic/architecture/ui-layer/events) recommends
  handling business actions in the state holder and reflecting their results in UI state. The visible
  retry label describes the exact business behavior users will receive.
- [Kotlin coroutine cancellation](https://kotlinlang.org/docs/cancellation-and-timeouts.html) remains
  the boundary for explicit stop/leave behavior; cancellation clears the in-memory checkpoint and is
  never converted to a retryable failure.
- Fixed behavior references remain Legado
  [`3a7c4daaf79c652b8e60d6aad80f665bbf6cacba`](https://github.com/apgk/legado/tree/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba)
  ([GPL-3.0](https://github.com/apgk/legado/blob/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba/LICENSE))
  and Readium
  [`1b1f6b308a7b6f968b2bf1e66c84912879466f75`](https://github.com/readium/kotlin-toolkit/tree/1b1f6b308a7b6f968b2bf1e66c84912879466f75)
  ([BSD-3-Clause](https://github.com/readium/kotlin-toolkit/blob/1b1f6b308a7b6f968b2bf1e66c84912879466f75/LICENSE)).
  V76 is an independent state/checkpoint implementation; it copies no GPL code or native bridge.

## Regression evidence required for the final commit

- `AiValidationRetryCheckpointV76Test` proves a failed catalogue stage does not call the model again
  for validated search rules, and a content transport failure does not call it again for validated
  search or catalogue rules. Both retries must still fetch the live pages again.
- `SourceBrowserSessionV56DeviceTest#failedValidationOffersCheckpointRetryAndKeepsTransportChoiceExplicit`
  renders the real Compose screen, verifies both transport choices, and clicks them in order.
- The unfiltered Reader and Browser device groups, all JVM tests, Debug build, lint, EPUB process
  recovery and browser process recovery must pass on the final commit. Prior success is not a
  substitute for that run.

## Not claimed

This does not persist unfinished AI rules across process death, automatically retry provider or
network failures, bypass website verification, validate a real AI account, or prove behavior on a
physical device. Real provider billing behavior depends on the provider; V76 only proves that
Langhuan does not issue duplicate earlier-stage `generateText` calls in the covered retry chain.
