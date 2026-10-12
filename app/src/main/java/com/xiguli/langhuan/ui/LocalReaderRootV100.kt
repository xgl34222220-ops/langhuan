package com.xiguli.langhuan.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.epub.EpubReaderActivity
import com.xiguli.langhuan.ui.epub.EpubReaderEntry
import com.xiguli.langhuan.ui.epub.ReaderEditionPreferenceV90
import com.xiguli.langhuan.ui.epub.ReaderEditionV90
import kotlinx.coroutines.delay

/**
 * Local-only root: 书架 is home (no bottom bar). The title opens 书架列表, + imports, the person
 * icon opens 我的, long-press opens the book sheet, and 查看详情 opens 图书详情.
 */
@Composable
fun LocalReaderRootV100(externalBooks: ExternalBookImportCoordinatorV1? = null) {
    val vm: LocalReaderViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val importVm: LocalBookImportViewModelV1 = viewModel()
    val importState by importVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appContext = context.applicationContext

    // Routes: "shelf", "shelves", "mine", "reader", "detail:<id>".
    var route by rememberSaveable { mutableStateOf("shelf") }
    var sheetBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var moveBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteBookId by rememberSaveable { mutableStateOf<String?>(null) }

    if (route == "reader" && state.libraryLoaded && state.openedBook == null && !state.isBusy) route = "shelf"
    com.xiguli.langhuan.ui.design.PaperReaderSystemBarsV44(
        lightBackground = !isSystemInDarkTheme(),
        readerActive = route == "reader",
    )

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importVm.importUri(uri)
    }
    LaunchedEffect(importState.importedBookId) {
        val id = importState.importedBookId ?: return@LaunchedEffect
        vm.rememberImportTarget(id)
        importVm.consumeImportedBook()
    }
    LaunchedEffect(importState.message, importState.error, importState.externalRequestUri) {
        // An import opened from another app reports its result in its own dialog.
        if (importState.externalRequestUri != null) return@LaunchedEffect
        val text = importState.error ?: importState.message ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        importVm.clearFeedback()
    }
    LaunchedEffect(state.message) {
        val text = state.message ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        vm.clearMessage()
    }

    var epubOpening by remember { mutableStateOf(false) }
    val epubLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        epubOpening = false
        vm.refreshShelf()
        if (result.resultCode == EpubReaderActivity.RESULT_TEXT_READER) {
            result.data?.getStringExtra(EpubReaderActivity.EXTRA_BOOK_ID)?.let { id -> vm.openBook(id) { route = "reader" } }
        }
    }
    fun openBook(id: String) {
        if (epubOpening || state.isBusy) return
        val epub = EpubReaderEntry.isEpub(appContext, id)
        val prefs = ReaderEditionPreferenceV90.prefs(appContext)
        if (epub && !ReaderEditionPreferenceV90.opensAsText(prefs, id)) {
            epubOpening = true
            try {
                epubLauncher.launch(EpubReaderEntry.intent(appContext, id))
            } catch (error: Exception) {
                epubOpening = false
                Toast.makeText(context, "无法打开 EPUB 原版：${error.message.orEmpty()}", Toast.LENGTH_SHORT).show()
            }
        } else vm.openBook(id) { route = "reader" }
    }
    fun openOriginalEdition(id: String) {
        ReaderEditionPreferenceV90.save(ReaderEditionPreferenceV90.prefs(appContext), id, ReaderEditionV90.ORIGINAL)
        vm.closeBook()
        route = "shelf"
        openBook(id)
    }

    if (externalBooks != null) ExternalBookImportHostV1(externalBooks, importVm)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when {
                route == "reader" -> {
                    BackHandler {
                        vm.closeBook()
                        route = "shelf"
                    }
                    if (state.openedBook != null) {
                        LocalReaderHostV100(
                            vm = vm,
                            onBack = {
                                vm.closeBook()
                                route = "shelf"
                            },
                            onOpenOriginalEdition = state.openedBook?.id
                                ?.takeIf { EpubReaderEntry.isEpub(appContext, it) }
                                ?.let { id -> { openOriginalEdition(id) } },
                        )
                    }
                }
                route == "shelves" -> {
                    BackHandler { route = "shelf" }
                    ShelfListPageV100(
                        state = state,
                        onBack = { route = "shelf" },
                        onSelectGroup = { id ->
                            vm.selectGroup(id)
                            route = "shelf"
                        },
                        onCreateGroup = { vm.createGroup(it) },
                        onRenameGroup = { id, name -> vm.renameGroup(id, name) },
                        onDeleteGroup = { vm.deleteGroup(it) },
                        onOpenBook = ::openBook,
                    )
                }
                route == "mine" -> LocalMinePageV101(
                    books = state.books,
                    shelfCount = state.shelf.groups.size,
                    onBack = { route = "shelf" },
                    onOpenBook = ::openBook,
                )
                route.startsWith("detail:") -> {
                    val id = route.removePrefix("detail:")
                    val book = state.books.firstOrNull { it.id == id }
                    if (book == null) {
                        if (state.libraryLoaded) route = "shelf"
                    } else {
                        LaunchedEffect(id) { vm.loadDetail(id) }
                        BookDetailPageV100(
                            book = book,
                            groupName = state.shelf.groups.firstOrNull { it.id == book.groupId }?.name.orEmpty(),
                            detail = state.detail?.takeIf { it.bookId == id },
                            onBack = {
                                vm.clearDetail()
                                route = "shelf"
                            },
                            onSave = { title, author, cover ->
                                vm.saveDetails(id, title, author, cover)
                                vm.clearDetail()
                                route = "shelf"
                            },
                            onImportCover = { uri -> vm.importCoverImage(id, uri) },
                        )
                    }
                }
                else -> LocalShelfHomeV100(
                    state = state,
                    importState = importState,
                    onOpenBook = ::openBook,
                    onLongPressBook = { sheetBookId = it },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onCancelImport = importVm::cancelImport,
                    onMine = { route = "mine" },
                    onOpenShelfList = { route = "shelves" },
                )
            }
        }
    }

    sheetBookId?.let { id ->
        val book = state.books.firstOrNull { it.id == id }
        if (book == null) sheetBookId = null
        else BookActionSheetV100(
            book = book,
            onDismiss = { sheetBookId = null },
            onDetail = {
                sheetBookId = null
                route = "detail:$id"
            },
            onMove = {
                sheetBookId = null
                moveBookId = id
            },
            onDelete = {
                sheetBookId = null
                deleteBookId = id
            },
        )
    }
    moveBookId?.let { id ->
        val book = state.books.firstOrNull { it.id == id }
        if (book == null) moveBookId = null
        else MoveShelfSheetV100(
            book = book,
            groups = state.shelf.groups,
            onDismiss = { moveBookId = null },
            onPick = { group ->
                moveBookId = null
                if (group != book.groupId) vm.moveBook(id, group)
            },
            onCreate = { name ->
                moveBookId = null
                vm.createGroup(name, moveBookId = id)
            },
        )
    }
    deleteBookId?.let { id ->
        val book = state.books.firstOrNull { it.id == id }
        if (book == null) deleteBookId = null
        else DeleteBookDialogV100(
            book = book,
            onDismiss = { deleteBookId = null },
            onConfirm = {
                deleteBookId = null
                vm.deleteBook(id)
            },
        )
    }
}

/**
 * Keeps the V30 reading surface mounted for the whole session; only a real lifecycle resume
 * gets the short stabilisation delay (recents / gesture false turns).
 */
@Composable
private fun LocalReaderHostV100(
    vm: LocalReaderViewModel,
    onBack: () -> Unit,
    onOpenOriginalEdition: (() -> Unit)?,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeRequested by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var readerMounted by remember { mutableStateOf(resumeRequested) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumeRequested = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    resumeRequested = false
                    readerMounted = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeRequested) {
        if (!resumeRequested) {
            readerMounted = false
            return@LaunchedEffect
        }
        delay(120)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) readerMounted = true
    }
    ReaderWindowSessionV27(resumeRequested)
    ReaderEngineV30(
        viewModel = vm,
        onBackToShelf = onBack,
        interactionEnabled = readerMounted,
        onOpenOriginalEdition = onOpenOriginalEdition,
    )
}
