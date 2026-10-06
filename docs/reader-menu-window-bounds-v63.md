# Reader menu window bounds V63

## User-visible gap

The V30 reader menu capped its sheet and scrollable panels with
`LocalConfiguration.current.screenHeightDp`. That value describes the device
screen configuration, not necessarily the app's current window. In split-screen,
desktop/freeform windows, and some rotated dialog layouts, the menu could reserve
more height than the window actually owns and push controls outside the visible
or touchable area.

## Change

- `ReaderMenuV30` now derives all eight menu height limits from
  `LocalWindowInfo.current.containerSize.height` and the current density.
- The existing proportions (`0.88`, `0.58`, `0.44`, `0.42`, and `0.62`) remain
  unchanged. Safe-drawing, navigation-bar, IME padding, scrolling, navigation,
  reading position, bookmarks, and persisted settings are unchanged.
- `ReaderMenuWindowBoundsV63Test` prevents the menu from returning to full-screen
  `LocalConfiguration` dimensions.
- The existing `ReaderRecreationV42DeviceTest` remains the real-window check: it
  rotates the authorized emulator, verifies that the scroll viewport and physical
  font controls stay inside the active accessibility window, injects actual touch,
  and confirms the final persisted value is still 21sp.

## References and adoption boundary

- Android's [WindowInfo API](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/WindowInfo)
  defines the current window container size for application layout.
- Android's [different display sizes guide](https://developer.android.com/develop/adaptive-apps/guides/support-different-display-sizes)
  explicitly includes resizable split-screen and desktop windows in adaptive
  layout requirements.
- The fixed Legado and Readium revisions listed in PR #104 remain comparison
  references only. No GPL implementation, rule interpreter, native bridge, or
  Readium internals were copied.

## Acceptance boundary

This batch must run the same unfiltered Reader, Browser, JVM, Debug, and lint
workflows as the preceding batch, including both real seed / force-stop / restore
process checks. Previous successful runs do not replace this batch's evidence.
Real AI services, third-party login or CAPTCHA, physical devices, and API 28
renderer reclamation remain outside the authorized environment.
