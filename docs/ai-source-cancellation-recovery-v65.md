# AI source cancellation recovery V65

## User-visible gap

The explicit **停止生成** action already invalidated the generation token, cancelled the coroutine, and
discarded partial rules. However, it also returned the AI source screen to the same blank status as a
never-started request. On a slow or abnormal source, a user could not tell whether Stop had taken
effect or whether work was still pending. The existing site URL and test-book inputs were already
owned by `LanghuanRootV4` with `rememberSaveable`, but the screen did not explain that they were
still available for a safe retry.

## Independent change

- `OnlineBooksStateV36.aiStopped` is an explicit, non-error terminal state for a user cancellation.
- `cancelAi()` still increments the generation token and cancels the job first. Its state transition
  clears in-flight steps, generated reports, and stale errors, then publishes `aiStopped = true`.
  No partial rule can be saved.
- The active V3 AI source screen shows a neutral **生成已停止** card confirming that the site URL and
  test-book name remain available. The existing ordinary and browser-mode actions are then the retry
  choices; their behavior and validation are unchanged.
- A new valid start, including a rejected invalid start, clears the stopped marker so cancellation
  feedback cannot be mistaken for the current attempt.
- `AiSourceCancellationRecoveryV65Test` exercises the real state transition and guards the
  ViewModel-to-screen wiring and visible recovery copy.

Android's guidance treats user input as events that update observable UI state, and warns not to
consume `CancellationException`. This implementation keeps the existing rethrow and makes the
explicit user event observable without turning cancellation into an error:

- <https://developer.android.com/develop/ui/compose/architecture>
- <https://developer.android.com/topic/architecture/ui-layer/state-production>
- <https://developer.android.com/kotlin/coroutines/coroutines-best-practices>

The pinned Legado `3a7c4daaf79c652b8e60d6aad80f665bbf6cacba` (GPL-3.0) and Readium
`1b1f6b308a7b6f968b2bf1e66c84912879466f75` (BSD-3-Clause) revisions remain behavior and lifecycle
references only. No code was copied from either project, and no native bridge was added.

## Safety and acceptance boundary

This batch does not persist AI output, change source parsing, relax browser verification, or alter
saved source replacement protection. Existing legal fixture tests for cancellation/retry, verification
pages, browser profile reuse, Cookie/DOM process restoration, generated search/catalogue/body, EPUB
bytes/artwork/scripts/bookmarks/Locator, draft cancellation, database transactions, and independent
copies remain mandatory.

Fresh unfiltered Reader, Browser, and JVM reports, Debug build, lint, and both real seed / force-stop /
restore process checks are required for this commit. The preceding 148 Reader, 16 Browser, and 718 JVM
results are baselines only. Real AI services, third-party login or CAPTCHA, physical devices, API 28
renderer reclamation, real split-screen/freeform windows, and the intermittent emulator
`device offline` root cause remain outside the authorized environment.
