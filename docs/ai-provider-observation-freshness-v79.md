# AI provider observation freshness V79

## Proven gap

V78 correctly resolved an empty or removed observed provider id against the current Room snapshot. The
observed id, however, is produced by the default-provider Flow rather than by an independent selection
control. If a user changed the default service and immediately started generation before that Flow
callback arrived, the previous id still existed in the table. V78 therefore retained it and the accepted
attempt could use the old service even though the database already had a different current default.

## Change

- The ViewModel records the ordered provider identity list together with the Flow-derived id and
  captures both for one accepted attempt.
- `selectAiProviderIdV79()` retains the observed preference only while that identity order still
  matches the current database snapshot. A default switch changes the order, so the repository selects
  the current first-priority provider. Empty, removed and not-yet-observed states keep the V78 fallback.
- Configuration and API-key reads stay inside the existing repository and encrypted key-store
  boundary. The generation, cancellation, validation-checkpoint, save-transaction and browser-session
  lifecycles are unchanged.

## Independent acceptance

- `AiProviderObservationFreshnessV79Test` proves that a changed priority snapshot selects the current
  default even when the old id still exists, while a matching snapshot retains a valid observed
  preference and an empty current table remains empty.
- `SourceBrowserSessionV56DeviceTest#currentDefaultWinsWhenObservedProviderPriorityIsStale` writes two
  unique providers through real Room/encrypted storage, switches the database default, injects the
  deliberately stale Flow observation, and proves repository resolution returns the new model. It then
  starts the real ViewModel/browser-mode builder on an original isolated WebView fixture, reaches
  “读取网站首页”, explicitly stops, and restores the prior provider state.

The device method deliberately stops before a model response. It does not claim a real AI account or
public website was exercised. Existing source identity, URL/book input ownership, V76/V77 checkpoints,
bookmarks, EPUB positions, files, illustrations and script-safety assertions are unchanged.

## References and licence boundary

Android state-production guidance treats durable repositories as the source of truth and UI-observed
state as a lifecycle-aware projection:
https://developer.android.com/topic/architecture/ui-layer/state-production

Legado commit `3a7c4daaf79c652b8e60d6aad80f665bbf6cacba` (GPL-3.0) and Readium commit
`1b1f6b308a7b6f968b2bf1e66c84912879466f75` (BSD-3-Clause) remain fixed behavioural and lifecycle
references only. No GPL code, rule implementation, or native bridge is copied.
