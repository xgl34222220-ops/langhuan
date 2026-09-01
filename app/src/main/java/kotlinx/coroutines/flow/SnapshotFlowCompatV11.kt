package kotlinx.coroutines.flow

import androidx.compose.runtime.snapshotFlow as composeSnapshotFlow

/** Keeps Reader V11's flow imports explicit while delegating to Compose runtime. */
internal fun <T> snapshotFlow(block: () -> T): Flow<T> = composeSnapshotFlow(block)
