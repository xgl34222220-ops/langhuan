# AI provider attempt revision V81

## Proven gap

V80 keeps the service identity shown by an active AI-source attempt aligned with the configuration
resolved for that attempt. The configuration, including its API key, is intentionally copied into the
builder. If that exact service was deleted or edited while website validation was still running, the
old in-memory configuration could nevertheless continue into later model stages.

## Change

- Provider observations now include the Room row revision used by the accepted attempt.
- A default-service switch only changes priority and does not interrupt the accepted service.
- Deleting or editing the accepted service invalidates the attempt generation, cancels its job, clears
  validated-rule checkpoints and reports that the service changed before offering a fresh retry.
- The revision and resolved identity remain process-memory state. No API key, provider choice or
  generated rule is newly persisted.

Cancellation cannot retract a request already accepted by a remote model. It prevents later stages and
uses coroutine/transport cancellation where the existing gateway supports it; no stronger remote
cancellation claim is made.

## Independent acceptance

- `AiProviderAttemptRevisionV81Test` proves that deletion and revision changes invalidate an attempt,
  while a default-only switch with the same service revision does not. It also proves checkpoint and
  identity cleanup.
- `SourceBrowserSessionV56DeviceTest#deletingTheResolvedProviderStopsBeforeLaterAiStages` uses two
  providers stored through real Room/encrypted storage and the real ViewModel. It blocks an original
  isolated WebView fixture after resolution, deletes the accepted service, and proves the attempt stops
  with a specific recovery message before the fixture is released.

The device method does not use a real AI account and does not claim cancellation of a request already
received by an external provider.

## References and licence boundary

Android state-production and cancellation guidance:
https://developer.android.com/topic/architecture/ui-layer/state-production
https://developer.android.com/kotlin/coroutines/coroutines-best-practices

Legado commit `3a7c4daaf79c652b8e60d6aad80f665bbf6cacba` (GPL-3.0) and Readium commit
`1b1f6b308a7b6f968b2bf1e66c84912879466f75` (BSD-3-Clause) remain behavioural and lifecycle
references only. No GPL code, rule or native bridge is copied.
