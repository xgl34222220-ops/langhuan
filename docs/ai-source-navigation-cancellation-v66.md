# AI source navigation cancellation V66

## Proven user-visible gap

The active V3 AI source screen accepted two ways to leave: its header arrow and the Android system Back
action. While a slow ordinary or browser-mode generation was running, the header invoked only the
navigation callback, leaving the ViewModel coroutine and browser work active after the screen
disappeared. System Back was handled only by the root route and moved all the way to the shelf, also
without cancelling that work. The two controls therefore disagreed, and either could continue
network/model activity that the user could no longer see.

## Independent change

- `AiBookSourceScreenV50` now owns one `leaveScreen` action for both its header arrow and a local
  Compose `BackHandler`.
- When and only when `aiRunning` is true, leaving first calls the existing `cancelAi()` path, which
  invalidates the generation token, cancels the coroutine, discards partial rules, and publishes the
  V65 **生成已停止** recovery state. It then performs the existing navigation callback.
- When no generation is running, Back navigates normally and does not create a false stopped state.
- The local handler also makes Android Back match the header: it returns to source management rather
  than bypassing that level through the root safety handler.
- Existing `rememberSaveable` ownership of the site URL and test-book name is unchanged, so returning
  to the screen keeps the inputs and exposes the ordinary/browser retry actions.

`SourceBrowserSessionV56DeviceTest#leavingActiveAiGenerationCancelsBeforeHeaderAndSystemBack`
uses the actual Compose header click and Android global Back action. It verifies cancellation precedes
navigation for both active exits, the two input values remain present, and an idle exit navigates
without cancellation. The existing real cancellation, verification, renderer termination, profile,
search, catalogue, body, and force-stop tests remain unchanged.

Android's official Compose guidance recommends `BackHandler` for custom Back behavior, with the
innermost enabled handler receiving the event. Android's lifecycle guidance also makes ViewModel
coroutines survive UI recreation and navigation until the ViewModel is cleared, so this explicit
user-intent boundary is required instead of assuming screen disappearance cancels the work:

- <https://developer.android.com/develop/ui/compose/libraries#handling_the_system_back_button>
- <https://developer.android.com/topic/libraries/architecture/coroutines#viewmodelscope>

The pinned Legado `3a7c4daaf79c652b8e60d6aad80f665bbf6cacba` (GPL-3.0) and Readium
`1b1f6b308a7b6f968b2bf1e66c84912879466f75` (BSD-3-Clause) remain behavior and lifecycle
references only. No GPL implementation or native bridge is copied.

## Safety and acceptance boundary

This batch changes no parser, source persistence, browser verification policy, saved-source replacement,
reader position, EPUB bytes/artwork/scripts/bookmarks/Locator, draft cancellation, database transaction,
or independent-copy behavior. Partial AI rules still cannot be saved.

Fresh unfiltered Reader, full Browser, JVM, Debug, lint, and both real seed / force-stop / restore
process checks are required for this commit. The preceding 148 Reader, 16 Browser, and 720 JVM results
are baselines only. Real AI services, third-party login or CAPTCHA, physical devices, API 28 renderer
reclamation, real split-screen/freeform windows, and the intermittent emulator `device offline` root
cause remain outside the authorized environment.
