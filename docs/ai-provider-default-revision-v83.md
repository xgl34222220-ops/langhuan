# AI provider default-selection revision V83

## Proven gap

V81 treats `StoredAiProvider.revision` as the exact configuration identity accepted by an active
AI-source request, and promises that changing only the default service will not cancel that request.
The Room default-selection query still rewrote `updatedAt`, which is the revision source. If a request
used service A and the user selected B and then A again, A appeared reconfigured even though its URL,
protocol, model, API key and generation options were unchanged. The active request was stopped with a
misleading service-modified error.

## Change

- Default selection now updates only `isDefault`; it does not rewrite the configuration revision.
- Saving an actual service configuration still assigns a monotonic `updatedAt` revision, so deletion
  and reconfiguration continue to invalidate the accepted attempt exactly as V81 requires.
- Provider priority, live labels, V82 observation ordering, API-key storage, model requests, validated
  rule checkpoints, save transactions, input state and browser sessions are unchanged.

## Independent acceptance

- `AiProviderDefaultRevisionV83Test` proves that A → B → A default changes keep a stable accepted
  revision current, while a real revision change still invalidates it.
- `SourceBrowserSessionV56DeviceTest#defaultSwitchRoundTripKeepsTheAcceptedProviderRevision` stores
  two services through real Room/encrypted storage, blocks an isolated browser request after A is
  resolved, performs the real A → B → A default round trip, and verifies both the persisted revision
  and the running Compose UI before cancelling the fixture.

The device method uses an original isolated HTML fixture and no real AI account. It does not claim
remote cancellation, real-network behaviour or recovery of an already accepted external request.

## References and licence boundary

Android state-production and UI-event guidance:
https://developer.android.com/topic/architecture/ui-layer/state-production
https://developer.android.com/topic/architecture/ui-layer/events

Legado commit `3a7c4daaf79c652b8e60d6aad80f665bbf6cacba` (GPL-3.0) and Readium commit
`1b1f6b308a7b6f968b2bf1e66c84912879466f75` (BSD-3-Clause) remain behavioural and lifecycle
references only. No GPL code, rule or native bridge is copied.
