from pathlib import Path

library_path = Path("app/src/main/java/com/xiguli/langhuan/ui/LibraryExperience.kt")
library = library_path.read_text(encoding="utf-8")

old_state_tail = '''    val requestActivityReload: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)'''
new_state_tail = '''    val requestActivityReload: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val libraryLoaded: Boolean = false,
)'''
if library.count(old_state_tail) != 1:
    raise SystemExit(f"library state tail mismatch: {library.count(old_state_tail)}")
library = library.replace(old_state_tail, new_state_tail, 1)

old_emit = '''                    current.copy(stories = books, openedBook = opened)
'''
new_emit = '''                    current.copy(stories = books, openedBook = opened, libraryLoaded = true)
'''
if library.count(old_emit) != 1:
    raise SystemExit(f"library emit mismatch: {library.count(old_emit)}")
library = library.replace(old_emit, new_emit, 1)
library_path.write_text(library, encoding="utf-8")

shelf_path = Path("app/src/main/java/com/xiguli/langhuan/ui/ReaderShelfV9.kt")
shelf = shelf_path.read_text(encoding="utf-8")

old_call = '''                            shelfName = selectedShelf,
                            books = visibleBooks,
                            query = query,
'''
new_call = '''                            shelfName = selectedShelf,
                            books = visibleBooks,
                            libraryLoaded = state.libraryLoaded,
                            query = query,
'''
if shelf.count(old_call) != 1:
    raise SystemExit(f"shelf call mismatch: {shelf.count(old_call)}")
shelf = shelf.replace(old_call, new_call, 1)

old_sig = '''    shelfName: String,
    books: List<ReaderBookUi>,
    query: String,
'''
new_sig = '''    shelfName: String,
    books: List<ReaderBookUi>,
    libraryLoaded: Boolean,
    query: String,
'''
if shelf.count(old_sig) != 1:
    raise SystemExit(f"shelf signature mismatch: {shelf.count(old_sig)}")
shelf = shelf.replace(old_sig, new_sig, 1)

old_header = '''        LanghuanPageHeader(
            eyebrow = "琅嬛 · 阅读",
            title = shelfName,
            subtitle = "${books.size} 本 · 本地优先",
            actions = {
'''
new_header = '''        LanghuanPageHeader(
            title = shelfName,
            subtitle = if (libraryLoaded) "${books.size} 本" else "正在载入书架",
            actions = {
'''
if shelf.count(old_header) != 1:
    raise SystemExit(f"shelf header mismatch: {shelf.count(old_header)}")
shelf = shelf.replace(old_header, new_header, 1)

old_empty = '''        if (books.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
'''
new_empty = '''        if (!libraryLoaded) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        modifier = Modifier.width(88.dp).height(2.dp),
                        color = t.accent,
                    )
                    Text(
                        "正在载入书架",
                        Modifier.padding(top = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.mutedForeground,
                    )
                }
            }
        } else if (books.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
'''
if shelf.count(old_empty) != 1:
    raise SystemExit(f"shelf empty mismatch: {shelf.count(old_empty)}")
shelf = shelf.replace(old_empty, new_empty, 1)
shelf_path.write_text(shelf, encoding="utf-8")

workflow = '''name: Android CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-24.04
    timeout-minutes: 30

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "9.5.0"

      - name: Run unit tests and build Debug APK
        run: gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: Langhuan-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error
'''
Path(".github/workflows/android.yml").write_text(workflow, encoding="utf-8")
Path(__file__).unlink()
