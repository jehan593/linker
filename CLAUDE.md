# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Linker: an Android app that makes itself the default handler for http/https links and shows its
own browser-chooser UI instead of opening a browser directly. Nord color palette, Martian Mono
Nerd Font, Jetpack Compose UI (matches ownscreen/noter's visual identity — see sibling repos at
`../ownscreen`, `../noter`). Package `com.linker.app`, minSdk 26.

Core flow: user taps any link anywhere on the device → `LinkInterceptorActivity` shows a chooser
card (edit the URL, or pick a browser) → the chosen browser opens the (possibly edited)
URL directly via an explicit package intent. The chooser's header has a single close (X) icon in
its top-right corner (no footer Cancel/Manage buttons), and a right-aligned row of action icons —
Save, Copy, Share, Send to Notesnook — sits at the bottom of the card below the browser list.

## Commands

```sh
./gradlew assembleRelease    # build app/build/outputs/apk/release/app-release.apk (R8-minified, resource-shrunk — what CI ships)
./gradlew assembleDebug      # build app/build/outputs/apk/debug/app-debug.apk (local iteration only)
./gradlew build              # full build incl. lint/checks
```

- No test suite exists in this repo currently.
- `local.properties` needs `sdk.dir` pointing at an Android SDK (Windows: use forward slashes with
  an escaped drive colon, e.g. `sdk.dir=C\:/Users/jehan/android-sdk` — a bare `C:\...` path fails
  Gradle property parsing with a cryptic "filename syntax is incorrect" error).
- Build the APK and hand it off rather than installing to an emulator/adb yourself — the user
  tests on their own device.
- `versionCode`/`versionName` are overridable via `-PappVersionCode=`/`-PappVersionName=` — CI
  (`.github/workflows/build-apk.yml`) passes `github.run_number` so every push gets a strictly
  increasing versionCode (required for update checkers like Obtainium to see a new build) and its
  own tag/release rather than overwriting one shared release — same setup as ownscreen/noter.
  `app/debug.keystore` is a committed keystore (not the AGP-generated one) so CI and local builds
  always sign with the same key; a fresh CI-generated debug keystore each run would give every
  release a different signature and break in-place updates. The `release` build type reuses that
  same pinned `debug.keystore` signing key (see `app/build.gradle.kts`) so in-place updates via
  Obtainium keep working even though the shipped variant is release, not debug.
- The release build is minified (`isMinifyEnabled`), resource-shrunk (`isShrinkResources`), and
  ABI-filtered to `arm64-v8a`/`armeabi-v7a` (real phones only — no distributed APK needs x86/
  x86_64 emulator variants) — same lightest-possible-APK pass done for ownscreen/noter. The two
  Martian Mono weights actually used (regular, medium — no bold; the one `FontWeight.Bold` use in
  `SavedLinksScreen.kt`'s search highlight relies on synthesized/faux bold) are Latin-subset from
  the full Nerd Font (11k+ glyphs down to the ~345 codepoints/545 glyphs the app can render),
  matching the subset shipped by the sibling apps. The five icons the base
  `androidx.compose.material3`/`material-icons-core` set doesn't include (Bookmark, BookmarkBorder,
  DragHandle, Public, the AutoMirrored OpenInNew) are vendored as plain XML vector drawables under
  `res/drawable/ic_*.xml` instead of pulling in `material-icons-extended` (a large icon pack whose
  ~1000 other icons the app never uses) — neither ownscreen nor noter carry that dependency either.

## Architecture

Manual DI, no framework: `LinkerApplication` owns a single `AppContainer`
(`di/AppContainer.kt`), lazily building the Room `AppDatabase` and four repositories. Compose
screens reach it via `rememberAppContainer()` (`ui/AppContainerAccess.kt`). ViewModels are
constructed directly via `viewModelFactory { initializer { ... } }` at the call site (no
`ViewModelProvider.Factory` boilerplate) — same pattern as ownscreen's `AppDetailViewModel`.

**Two entry points, one shared data layer:**
- `MainActivity` — the launcher activity. Two tabs (`TabRow`, no back-stack navigation library
  since there's nothing to navigate *into* — both screens are flat lists): **Browsers**
  (`ManageBrowsersScreen`) to hide/reorder/rename, and **Saved Links** (`SavedLinksScreen`) for
  history. Shows a dismissable-by-becoming-true `DefaultBrowserBanner` whenever
  `DefaultBrowserRole.isHeld()` is false, rechecked on every `ON_RESUME` (covers the user coming
  back from the system role/settings screen without needing a manual refresh).
- `LinkInterceptorActivity` — what actually fires when a link is tapped elsewhere on the device
  (see manifest intent-filter below). Transient and translucent (`Theme.Linker.Transparent`):
  every exit path (pick a browser, close) calls `finish()`. Deliberately
  *not* `launchMode="singleTask"` — plain standard launch mode means each tap gets its own short-
  lived instance instead of needing `onNewIntent` plumbing to refresh a reused one. Also declares
  `android:taskAffinity=""` (see the manifest comment) — without it, this activity shares
  MainActivity's default task affinity, so if a MainActivity task already existed in the
  background, Android would bring that whole task forward instead of showing just the transient
  chooser, which felt like getting kicked into the Linker app itself rather than seeing a popup.

**Default-browser eligibility** (`AndroidManifest.xml`): `LinkInterceptorActivity`'s intent-filter
matches bare `ACTION_VIEW` for `http`/`https` with no host restriction — that's the specific signal
Android uses to offer an app under Settings → Apps → Default apps → Browser app, or via the
`RoleManager.ROLE_BROWSER` request flow (`util/DefaultBrowserRole.kt`, API 29+, falling back to
`Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS` below that). It deliberately does **not** set
`android:autoVerify` — that's for Android App Links to one verified domain, not a general browser
role.

**Browser enumeration** (`data/browser/InstalledBrowsersRepository.kt`): queries
`PackageManager.queryIntentActivities` for anything that resolves a bare `https://` URL — the same
signal used above, so "what counts as a browser" stays consistent between the manifest and the
in-app list. 5-minute TTL cache, same pattern as ownscreen's `InstalledAppsRepository`. The
`<queries>` block in the manifest grants the package-visibility needed to see them on Android 11+.

**Per-browser overrides** (`data/repository/BrowserPrefsRepository.kt`): merges that live installed
list with a `BrowserPrefEntity` Room table (hidden / custom label / order index) keyed by package
name. A browser with no row yet gets a synthesized order index (recomputed fresh on every merge, so
newly-installed browsers consistently land at the end) rather than a persisted one — and a newly
*uninstalled* browser just stops appearing on its own next merge, since `merge()` only ever iterates
over what `InstalledBrowsersRepository` currently reports, not over stale `BrowserPrefEntity` rows
(an orphaned row for an uninstalled package is harmless dead data, never rendered).

`observeManageList()`'s `Flow` only naturally re-emits when a `BrowserPrefEntity` row changes, so
installing/uninstalling a browser while Manage Browsers is already open wouldn't show up until some
unrelated pref changed or the screen was reopened. The refresh button (top-right of the Browsers
tab) works around that by bumping a `refreshTrigger: MutableStateFlow<Int>` that's `combine()`d with
the prefs `Flow` purely to force the merge to re-run its `getBrowsers(forceRefresh = true)` scan on
demand — the trigger's own value is never read, it's just a re-emission signal.

`setHidden`/`setCustomLabel` both call `applyOrder(currentList)` *before* writing their own field.
Earlier they only materialized the one browser being touched, leaving its still-unpersisted
siblings to get a fresh alphabetically-synthesized index next merge — and since that synthesis
starts counting right after the *persisted* max index, promoting one sibling to a real row shifted
where the counter started and silently reordered everyone else too (reported as "hiding/renaming
one browser reshuffles the whole list"). Locking in the full current order first, on every single
hide/rename, makes that class of action order-neutral for every browser it doesn't touch.

**Drag-and-drop reordering** (`ui/browsers/ManageBrowsersScreen.kt`): uses
`sh.calvin.reorderable:reorderable` on the `LazyColumn`. During a drag, `onMove` only updates a
local `mutableStateOf` list for immediate visual feedback — the DB isn't touched until
`onDragStopped`, when `BrowserPrefsRepository.applyOrder()` rewrites every item's `orderIndex` to
match its new position in one shot (simpler and more robust than trying to track individual swaps,
and cheap since reorders are infrequent user-driven events, not a hot path). The hidden/visible
state has a single control (the `Switch`) — no separate eye icon alongside it, since that was
redundant with what the switch's own position already shows.

**Saved links** (`data/repository/SavedLinksRepository.kt` → `SavedLinkEntity`): the bookmark
button in the chooser records the (possibly edited) URL plus `System.currentTimeMillis()`. Reopening
one from the Saved Links tab fires a plain `ACTION_VIEW` intent — if Linker is still the default
browser, this deliberately re-enters the chooser rather than jumping straight to a browser,
consistent with what tapping any other link does.

`SavedLinksRepository.save()` treats saving an already-saved URL (exact string match, after
trimming — no scheme/host normalization) as a bump rather than a duplicate: it updates the existing
row's `savedAtMillis` instead of inserting a second copy, so repeatedly saving the same link keeps
it "recently saved" and floats it back to the top rather than cluttering the list. Since that means
tapping Save again on an already-saved link only bumps a timestamp instead of doing anything new,
`LinkChooserViewModel.isAlreadySaved` looks the URL up via the same exact-match comparison
(`SavedLinksRepository.isSaved()`) on open and on every edit (cancelling any in-flight lookup so a
burst of edits can't let a stale one win) — independent of whether *this session* ever tapped Save.
The chooser's bookmark icon reflects that: filled and tinted `primary` (the same accent as the URL
text and the Saved Links Open action) once `isAlreadySaved` is true, outline and default-tinted
otherwise, so a filled bookmark reads as "already saved — tap to refresh its timestamp" rather than
looking identical to "not saved yet". Editing a saved
link's URL (`updateUrl`) deliberately leaves `savedAtMillis` alone — correcting the text isn't the
same event as (re-)saving it, so its place in the day-grouped list doesn't jump just because you
fixed a typo. Search (`SavedLinksViewModel`) is a plain case-insensitive substring match over the
URL text, applied client-side via `combine()` with the search query — no DB-level `LIKE` query,
since this list is small enough that filtering in memory is simpler and just as fast. `SavedLinksScreen`
groups the (possibly filtered) list into sticky day headers ("Today"/"Yesterday"/date) using
`java.time.LocalDate` (available natively at minSdk 26, no desugaring needed) — grouping happens
after filtering so a search still reads as day-organized rather than flattening into one list.

Each row's pieces are colored by role instead of all sharing the default content color: the URL
text is `primary` (reads as a link), the timestamp is muted `onSurfaceVariant`, and the action
icons are tinted individually — Edit, Copy, Share and Send neutral (`onSurfaceVariant`), Open
matches the link's own accent (`primary`, since it's what acts on that link), Delete uses `error`
— mirroring the same
role-based coloring already used for the rename dialog's Save/Cancel/Reset in
`ManageBrowsersScreen`. Search matches are highlighted (`highlightedUrlText` in
`SavedLinksScreen.kt`) using a fixed Nord `nord13`-on-`nord0` span rather than theme-relative
colors — a search highlight is meant to pop the same way regardless of dark/light mode or the link
text's own primary tint.

**No link preview**: the chooser only shows the domain (parsed from the URL) plus an editable text
field — it deliberately doesn't render the destination page. Opening a link never needs
`INTERNET` either, since that always hands off to the chosen browser's own process via an
explicit-package `Intent`. The only thing in the app that does touch the network is the Send
action below. The URL field uses a smaller-than-body text style and caps at 4 lines: a longer URL
scrolls inside the field itself rather than growing the whole card — otherwise a wrapping monster
URL would push the browser list and the footer action icons out of the popup.

**Send to Notesnook** (`data/remote/NotesnookApi.kt`, `data/repository/NotesnookRepository.kt`):
a Send icon button — next to Save in the link chooser, and alongside Edit/Open/Delete in each
Saved Links row — POSTs the link to the user's Notesnook inbox via its fixed Inbox API endpoint
(`https://inbox.notesnook.com/`), the same integration noter uses to send note text, adapted here
to send a link (wrapped as an HTML anchor) instead of free-form note content. This is the sole
reason the manifest carries `INTERNET` at all. The inbox API key and an optional tag ID are
per-account settings (Notesnook Settings → Inbox → Create Key), stored via a single Preferences
DataStore (`linker_settings`, see `NotesnookRepository`) rather than Room, since it's two opaque
strings rather than relational data — and shared globally rather than per-screen, since both send
sites POST through the same account. Each sent note is titled `Link: <url>` (rather than a dated
placeholder title), with the note body leading with the send timestamp
(`yyyy-MM-dd HH:mm - <url>`) — both deliberately deterministic from the link and send time alone,
unlike noter's dated "Note: NOTER - ..." title, since a link already reads fine as its own title.
They're configured through one full-page screen
(`ui/settings/NotesnookSettingsScreen.kt`) reached from a gear icon in `MainActivity`'s top bar,
with a back arrow to return,
because the chooser and Saved Links tab both need the same key/tag pair rather than each keeping
its own copy. Send results surface as a `Toast` (`LinkChooserViewModel.toastMessages` /
`SavedLinksViewModel.toastMessages`, collected via `LaunchedEffect` in each screen) rather than
noter's inline status line — Toast is already the established feedback pattern here (see
`LinkInterceptorActivity.openInBrowser`'s "Couldn't open that link" toast), and a persistent status
line doesn't fit as naturally into either the compact chooser card or a single list row.

## Theme

`ui/theme/Theme.kt`'s `NordDarkColorScheme`/`NordLightColorScheme` fill in every M3 color role
explicitly, not just the handful (`primary`, `surface`, `background`, ...) that read as "the theme"
— components like `Card` and `AlertDialog` actually pull their background from the newer
`surfaceContainer*` tiers, and anything left unset there quietly falls back to Material's own
baseline (purple-tinted) palette instead of Nord. In the dark scheme those container tiers are
deliberately kept within `nord0`/`nord1`/`nord2` and never touch `nord3`: an earlier pass had
`surfaceContainerHigh` equal to `outline` (both `nord3`), which made an `OutlinedTextField`'s border
colorwise identical to the popup card behind it (invisible) and made elevated surfaces read as too
bright against the near-black background. `outline` staying a full tier above every container color
is what keeps borders visible regardless of which exact container tier a given component defaults
to.

Dialog action buttons are materialized as bordered/tinted controls rather than unmaterialized
text labels so the affordances read as buttons (`ManageBrowsersScreen.kt`'s `RenameBrowserDialog`):
the emphasized action (`Save`) is a `FilledTonalButton`, and the mild "this undoes something"
action (`Reset` — which discards the custom name back to the system one) is an `OutlinedButton`
tinted Nord yellow (`nord13`, dark-amber in light mode). The full-page Notesnook settings shares
the same button convention. `LinkerTheme` also supplies a custom `Shapes` (rounded corners) gutter
so all M3 components pick up Nord-friendly geometry.

Dialog headers are a custom `Dialog` + `Card` composition, not `AlertDialog`: the title sits next
to a close (X) `IconButton` in the top-right corner (`RenameBrowserDialog`, `EditSavedLinkDialog`)
instead of the default text-label Cancel button — same close-affordance pattern as the link
chooser's header. The chooser's footer row is likewise icon buttons (Save/Copy/Share/Send), not
text buttons.

The Notesnook settings screen is a full page shown by toggling `isNotesnookSettingsOpen` in
`MainActivity` and rendered inside an `AnimatedContent` so it slides in from the right like a
navigation push. Because it's a plain boolean swap rather than a navigation destination, a
`BackHandler(enabled = isNotesnookSettingsOpen)` intercepts the system back gesture/button to
return to the tabs — without it the whole Activity would just finish.

## Fonts

Martian Mono Nerd Font ships as bundled `.ttf`s under `res/font/` (license in
`app/licenses/MARTIAN_MONO_LICENSE.txt` — kept out of `res/` since Android's resource merger
rejects non-`.xml`/`.ttf`/`.ttc`/`.otf` files there), applied via `ui/theme/Type.kt` exactly like
ownscreen/noter.
