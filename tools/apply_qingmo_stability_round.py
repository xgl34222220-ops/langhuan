from pathlib import Path

# 1) Shelf: remove AnimatedContent fade/white-flash transitions. Switching tabs must be an immediate
# stable content swap like the reference reader, not a whole-page crossfade.
shelf_path = Path('app/src/main/java/com/xiguli/langhuan/ui/ReaderShelfV9.kt')
shelf = shelf_path.read_text(encoding='utf-8')
shelf = shelf.replace('import androidx.compose.animation.AnimatedContent\n', '', 1)
old = '''                AnimatedContent(page, label = "reader-shelf-v9-page") { current ->
                    when (current) {'''
new = '''                when (page) {'''
if old not in shelf:
    raise SystemExit('shelf AnimatedContent anchor not found')
shelf = shelf.replace(old, new, 1)
old_tail = '''                        )
                    }
                }
            }

            ReaderBottomNavV9('''
new_tail = '''                        )
                }
            }

            ReaderBottomNavV9('''
if old_tail not in shelf:
    raise SystemExit('shelf AnimatedContent closing anchor not found')
shelf = shelf.replace(old_tail, new_tail, 1)
shelf_path.write_text(shelf, encoding='utf-8')

# 2) Reader: wire every Qingmo-style action to a distinct real state/action and use the cleaned
# StoryExperience instead of exposing the old V17 component stack from the reader route.
reader_path = Path('app/src/main/java/com/xiguli/langhuan/ui/reader/ReaderExperience.kt')
reader = reader_path.read_text(encoding='utf-8')
old_story = '''        ReaderExperienceRoute.STORY -> Box(Modifier.fillMaxSize()) {
            StoryPlayPanelV17(
                book = book,
                libraryState = state,
                aiReady = studioState.provider.ready,
                onAiSetup = onOpenAiSetup,
                onAdopted = { viewModel.openBook(book.id) },
            )
            ReaderFloatingButton('''
new_story = '''        ReaderExperienceRoute.STORY -> Box(Modifier.fillMaxSize()) {
            StoryExperience(
                book = book,
                libraryState = state,
                aiReady = studioState.provider.ready,
                onAiSetup = onOpenAiSetup,
                onAdopted = { viewModel.openBook(book.id) },
            )
            ReaderFloatingButton('''
if old_story not in reader:
    raise SystemExit('reader story route anchor not found')
reader = reader.replace(old_story, new_story, 1)

old_params = '''                    bookmarked = bookmarked,
                    canPrevious = previous != null,
                    canNext = next != null,
                    onBack = { saveCurrentProgress(); onBack() },'''
new_params = '''                    bookmarked = bookmarked,
                    canPrevious = previous != null,
                    canNext = next != null,
                    fontKey = fontKey,
                    lineFactor = lineFactor,
                    pageModeKey = pageModeKey,
                    onBack = { saveCurrentProgress(); onBack() },'''
if old_params not in reader:
    raise SystemExit('reader Qingmo state params anchor not found')
reader = reader.replace(old_params, new_params, 1)

old_actions = '''                    onSearch = { showSearch = true },
                    onNight = { themeKey = if (themeKey == "night") "paper" else "night" },
                    onSettings = { showSettings = true },'''
new_actions = '''                    onSearch = { showSearch = true },
                    onNight = { themeKey = if (themeKey == "night") "paper" else "night" },
                    onFontKey = { key -> saveCurrentProgress(); fontKey = key },
                    onLineFactor = { value -> saveCurrentProgress(); lineFactor = value },
                    onPageMode = { key -> saveCurrentProgress(); pageModeKey = key },
                    onSettings = { showSettings = true },'''
if old_actions not in reader:
    raise SystemExit('reader Qingmo callbacks anchor not found')
reader = reader.replace(old_actions, new_actions, 1)
reader_path.write_text(reader, encoding='utf-8')

# 3) Story main surface: keep the player-facing screen quiet. Context/branches/runtime tools remain in
# management instead of three competing header actions + a row of chips.
story_path = Path('app/src/main/java/com/xiguli/langhuan/ui/story/StoryExperience.kt')
story = story_path.read_text(encoding='utf-8')
old_story_header = '''                        IconButton(onClick = { showContext = true }) { Icon(Icons.Rounded.Explore, "世界状态", tint = t.foreground) }
                        IconButton(onClick = { showBranches = true }) { Icon(Icons.Rounded.ForkRight, "故事分支", tint = t.foreground) }
                        IconButton(onClick = onManagement) { Icon(Icons.Rounded.Tune, "故事设置", tint = t.foreground) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StoryContextChip(Icons.Rounded.Place, location)
                        StoryContextChip(Icons.Rounded.Schedule, storyTime)
                        player?.name?.takeIf { it.isNotBlank() }?.let { StoryContextChip(Icons.Rounded.Person, it) }
                    }'''
new_story_header = '''                        IconButton(onClick = onManagement) { Icon(Icons.Rounded.MoreHoriz, "故事设置", tint = t.foreground) }
                    }'''
if old_story_header not in story:
    raise SystemExit('story cluttered header anchor not found')
story = story.replace(old_story_header, new_story_header, 1)
story_path.write_text(story, encoding='utf-8')
