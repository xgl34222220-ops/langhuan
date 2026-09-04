package com.xiguli.langhuan.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val BookEditJsonV5 = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

data class BookEditStateV5(
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class BookEditViewModelV5(application: Application) : AndroidViewModel(application) {
    private val db = LanghuanDatabase.get(application)
    private val storyDao = db.storyStateDao()
    private val _state = MutableStateFlow(BookEditStateV5())
    val state = _state.asStateFlow()

    fun save(bookId: String, title: String, genre: String, premise: String) {
        if (_state.value.busy || title.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching {
                val row = storyDao.get(bookId) ?: error("找不到这本小说")
                val snapshot = BookEditJsonV5.decodeFromString(StorySnapshot.serializer(), row.snapshotJson)
                val updated = snapshot.copy(
                    novel = snapshot.novel.copy(
                        title = title.trim(),
                        genre = genre.trim().ifBlank { "小说" },
                        premise = premise.trim(),
                    )
                )
                storyDao.upsert(
                    row.copy(
                        snapshotJson = BookEditJsonV5.encodeToString(StorySnapshot.serializer(), updated),
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            }.onSuccess {
                _state.update { it.copy(busy = false, message = "作品资料已保存") }
            }.onFailure { e ->
                _state.update { it.copy(busy = false, error = e.message ?: "保存失败") }
            }
        }
    }

    fun setLocalCover(bookId: String, uri: Uri) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching {
                val app = getApplication<Application>()
                val covers = File(app.filesDir, "covers").apply { mkdirs() }
                val target = File(covers, "$bookId-user-cover")
                app.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("无法读取封面图片")
                val row = storyDao.get(bookId) ?: error("找不到这本小说")
                val snapshot = BookEditJsonV5.decodeFromString(StorySnapshot.serializer(), row.snapshotJson)
                val updated = snapshot.copy(novel = snapshot.novel.copy(coverPath = target.absolutePath))
                storyDao.upsert(
                    row.copy(
                        snapshotJson = BookEditJsonV5.encodeToString(StorySnapshot.serializer(), updated),
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            }.onSuccess {
                _state.update { it.copy(busy = false, message = "封面已更新") }
            }.onFailure { e ->
                _state.update { it.copy(busy = false, error = e.message ?: "更换封面失败") }
            }
        }
    }

    fun clearFeedback() = _state.update { it.copy(message = null, error = null) }
}

@Composable
fun BookEditPageV5(
    book: ReaderBookUi,
    editViewModel: BookEditViewModelV5,
    onClose: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val editState by editViewModel.state.collectAsStateWithLifecycle()
    var title by remember(book.id, book.title) { mutableStateOf(book.title) }
    var genre by remember(book.id, book.genre) { mutableStateOf(book.genre) }
    var premise by remember(book.id, book.premise) { mutableStateOf(book.premise) }
    val cover = remember(book.coverPath) {
        book.coverPath.takeIf { it.isNotBlank() }
            ?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    val coverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) editViewModel.setLocalCover(book.id, uri)
    }

    Surface(Modifier.fillMaxSize(), color = t.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, "返回", tint = t.foreground) }
                Column(Modifier.weight(1f)) {
                    Text("编辑书籍", style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
                    Text("书名、封面、类型和简介", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                    Surface(
                        modifier = Modifier.width(136.dp).aspectRatio(.68f),
                        shape = RoundedCornerShape(16.dp),
                        color = t.muted,
                        shadowElevation = 2.dp,
                    ) {
                        if (cover != null) {
                            Image(
                                cover.asImageBitmap(),
                                book.title,
                                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("暂无封面", color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Button(
                            onClick = { coverLauncher.launch("image/*") },
                            enabled = !editState.busy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp))
                            Text("更换封面", Modifier.padding(start = 7.dp))
                        }
                        Text(
                            "从相册或文件选择竖版图片，保存后立即同步到书架。",
                            Modifier.padding(top = 9.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                    }
                }

                Text("作品资料", Modifier.padding(top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.labelLarge, color = t.mutedForeground)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("书名") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    label = { Text("类型") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = premise,
                    onValueChange = { premise = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    label = { Text("简介") },
                    minLines = 5,
                    maxLines = 9,
                    shape = RoundedCornerShape(16.dp),
                )

                Button(
                    onClick = { editViewModel.save(book.id, title, genre, premise) },
                    enabled = !editState.busy && title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = t.primary, contentColor = t.primaryForeground),
                ) {
                    if (editState.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.primaryForeground)
                    else Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                    Text(if (editState.busy) "正在保存" else "保存修改", Modifier.padding(start = 8.dp))
                }

                editState.message?.let {
                    Text(it, Modifier.padding(top = 12.dp), color = t.success, style = MaterialTheme.typography.bodySmall)
                }
                editState.error?.let {
                    Text(it, Modifier.padding(top = 12.dp), color = t.destructive, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.navigationBarsPadding().height(28.dp))
            }
        }
    }
}
