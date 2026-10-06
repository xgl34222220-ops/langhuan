# AI provider immediate resolution V78

## Proven gap

The AI-source ViewModel previously treated the asynchronously collected `activeProviderId` as
the database authority. Immediately after opening the page, the first Room `Flow` value may still
be pending. The same cached id can also become stale when a provider is replaced. In either case an
accepted generation request could report “请先在设置里添加并启用一个 AI 服务” even though the
current provider table already contained an enabled priority provider.

## Change

- `AiProviderDao.allByPriority()` reads one current provider snapshot using the same default and
  update ordering as the UI flow.
- `activeProviderConfig()` keeps a still-existing observed id as the preference, otherwise falls
  back to the first current provider. Configuration and API-key lookup remain inside the existing
  repository and encrypted key store boundary.
- `buildWithAi()` captures the observed preference for the accepted attempt, resolves it against
  the current database snapshot, checks coroutine cancellation, and generation-guards the genuine
  no-provider terminal state. No provider exists still produces the existing actionable message.

## Independent acceptance

- `AiProviderImmediateResolutionV78Test` covers null, stale, empty, and still-current selection.
- `SourceBrowserSessionV56DeviceTest#configuredProviderIsResolvedImmediatelyWhenObservedIdIsStale`
  writes a uniquely named provider through the real Room repository and encrypted key store, forces
  the ViewModel's observed preference to a removed id, and verifies that the real ViewModel enters
  the genuine “读取网站首页” builder step rather than publishing the false no-provider error. It uses
  an original isolated browser fixture, then explicitly cancels and confirms the stopped non-error
  state. The temporary provider and key are deleted and the previous default is restored.

The device method deliberately cancels before any model result and does not validate a real AI
account or public website. Existing URL/book input ownership, validation checkpoints, cancellation
generation, save transactions, source identity, browser transport/session boundaries, EPUB
position, bookmarks, files and scripts are unchanged.

## References and licence boundary

Android's state-production and UI-event guidance supports resolving durable data in the state holder
rather than treating an asynchronously delivered presentation cache as authority. Legado commit
`3a7c4daaf79c652b8e60d6aad80f665bbf6cacba` (GPL-3.0) and Readium commit
`1b1f6b308a7b6f968b2bf1e66c84912879466f75` (BSD-3-Clause) remain fixed behavioural and lifecycle
references only. No GPL code, rule implementation, or native bridge is copied.


## Validation history before the final commit

The implementation commit `fde53967f959f4959d5e2c870470e3132d7df3d1` reached product and JVM
compilation, but both device workflows stopped at `compileDebugAndroidTestKotlin` before any device
test or process-recovery phase ran. The new test named the existing fixture-function parameter
`fixture`; its actual name is `pages`. Reader run
[37296138144](https://github.com/xgl34222220-ops/langhuan/actions/runs/37296138144) produced no
artifact. Browser run
[37296138148](https://github.com/xgl34222220-ops/langhuan/actions/runs/37296138148) preserved
artifact `11339086192` (37,627,789 bytes, SHA-256
`2f578121df31f3a0ef594bfca1fca1ccf856a0baef0239f0a47d787515a72576`). The correction changes
only that named argument; it does not alter product behavior, remove an assertion, or narrow a suite.
