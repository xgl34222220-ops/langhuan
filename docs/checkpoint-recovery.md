# Chapter Run recovery: persistence failures

The Coordinator retains a `COMMITTING` checkpoint when any post-save stage reports an unfinished write. It marks the run `COMPLETE` and removes the checkpoint only after all selected stages succeed or are explicitly skipped. This applies both with and without an AI gateway.

A retry keeps the original `runId`, saved version, successful stage markers, and returned model outputs. It reloads the saved story and retries only unfinished persistence. It does not create a new chapter version or repeat completed model calls. The existing Candidate/Canon, provenance, and retrieval paths remain in use.

## Durability boundary

`SharedPreferences.Editor.commit()` must acknowledge a successful disk write. A `false` result stops the run with a checkpoint persistence error before another paid request or side effect. Failed checkpoint removal is reported too. Optional model stages must propagate this error even when a streaming adapter wraps it.

A failed commit may already have changed the preferences' in-memory values. The error therefore means **durability was not acknowledged**, rather than claiming the disk definitely contains no data. If the process dies before any model output is durably saved, this patch cannot recover that unsaved output or guarantee the external provider will not charge again.

## Regression coverage

- One-shot Candidate write failure retains the paid Agent output and a recoverable checkpoint.
- Recreating the Coordinator and retrying performs exactly one missing structure write, with no extra SAVE, chapter version, or model request.
- The same recovery invariant covers full-book audit, execution settlement, Candidate staging, and autonomous-plan persistence.
- A failed local audit remains recoverable when no AI is configured.
- Real checkpoint serialization with fake preferences covers round-trip, deletion, false commit acknowledgements, and stopping before paid/side-effect stages.
- Optional streaming rewrites do not swallow checkpoint failures.

Run the project suite with:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

No device process-kill or physical disk-full test is implied by the unit tests above.

## References

- [Android `SharedPreferences.Editor.commit()`](https://developer.android.com/reference/android/content/SharedPreferences.Editor#commit()): check the write acknowledgement rather than ignoring it.
- [Android `SharedPreferences` caveats](https://developer.android.com/reference/android/content/SharedPreferences): in-memory state and successful durable persistence are not interchangeable.
- [LangGraph checkpointers](https://docs.langchain.com/oss/python/langgraph/checkpointers): retain completed task results and synchronize checkpoints before proceeding. The existing Kotlin run/checkpoint model already provides the appropriate local boundary; no workflow framework is added.
