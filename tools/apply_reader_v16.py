from pathlib import Path

reader_path = Path("app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderExperience.kt")
reader = reader_path.read_text(encoding="utf-8")

old_pages = '''    val scrollState = rememberScrollState()
    val pages = remember(chapter.id, readingText, fontSize, lineFactor, sidePadding, paragraphSpacing, fontKey) {
        splitReaderPagesV10(
            readingText,
            fontSize,
            lineFactor,
            sidePadding,
            paragraphSpacing,
        )
    }
    val pageOffsets = remember(readingText, pages) { readerPageStartOffsets(readingText, pages) }
'''
new_pages = '''    val scrollState = rememberScrollState()
    val measuredPagination = rememberReaderMeasuredPaginationV16(
        text = readingText,
        displayTitle = displayTitle,
        fontSize = fontSize,
        lineFactor = lineFactor,
        sidePadding = sidePadding,
        paragraphSpacing = paragraphSpacing,
        firstLineIndent = firstLineIndent,
        family = family,
    )
    val pages = measuredPagination.pages
    val pageOffsets = measuredPagination.offsets
'''
if reader.count(old_pages) != 1:
    raise SystemExit(f"reader page block mismatch: {reader.count(old_pages)}")
reader = reader.replace(old_pages, new_pages, 1)

old_layout_key = '''    val layoutKey = "$pageModeKey|$fontKey|${fontSize.roundToInt()}|${(lineFactor * 100).roundToInt()}|${sidePadding.roundToInt()}|${paragraphSpacing.roundToInt()}|$firstLineIndent"
'''
new_layout_key = '''    val layoutKey = "$pageModeKey|$fontKey|${fontSize.roundToInt()}|${(lineFactor * 100).roundToInt()}|${sidePadding.roundToInt()}|${paragraphSpacing.roundToInt()}|$firstLineIndent|${measuredPagination.layoutToken}"
'''
if reader.count(old_layout_key) != 1:
    raise SystemExit(f"layout key mismatch: {reader.count(old_layout_key)}")
reader = reader.replace(old_layout_key, new_layout_key, 1)

old_render = '''                                    firstLineIndent = firstLineIndent,
                                    family = family,
                                    background = palette.background,
'''
new_render = '''                                    firstLineIndent = firstLineIndent,
                                    indentFirstParagraph = measuredPagination.indentFirstParagraph.getOrElse(contentPage) { true },
                                    family = family,
                                    background = palette.background,
'''
if reader.count(old_render) != 1:
    raise SystemExit(f"paged renderer call mismatch: {reader.count(old_render)}")
reader = reader.replace(old_render, new_render, 1)

reader_path.write_text(reader, encoding="utf-8")

# Restore the normal read-only CI in the same migration commit. The running job has already loaded
# the temporary workflow, so it can safely restore itself before committing.
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
