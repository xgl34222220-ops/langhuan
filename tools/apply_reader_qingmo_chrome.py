from pathlib import Path

reader = Path('app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderExperience.kt')
text = reader.read_text(encoding='utf-8')

old_palette = 'private data class ReaderExperiencePalette(val background: Color, val foreground: Color, val secondary: Color, val chrome: Color)'
new_palette = 'internal data class ReaderExperiencePalette(val background: Color, val foreground: Color, val secondary: Color, val chrome: Color)'
if old_palette in text:
    text = text.replace(old_palette, new_palette, 1)

old_call = 'ReaderMatureChrome(\n                    modifier = Modifier.align(Alignment.Center),'
new_call = 'ReaderQingmoChrome(\n                    modifier = Modifier.align(Alignment.Center),'
if old_call not in text:
    raise SystemExit('ReaderMatureChrome call anchor not found')
text = text.replace(old_call, new_call, 1)
reader.write_text(text, encoding='utf-8')

workflow = Path('.github/workflows/android.yml')
workflow.write_text('''name: Android CI

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
''', encoding='utf-8')

Path('tools/apply_reader_qingmo_chrome.py').unlink()
