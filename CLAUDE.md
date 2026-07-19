# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Linker: an Android app that makes itself the default handler for http/https links and shows its
own browser-chooser UI instead of opening a browser directly. Nord color palette, Martian Mono
Nerd Font, Jetpack Compose UI (matches ownscreen/noter's visual identity — see sibling repos at
`../ownscreen`, `../noter`). Package `com.linker.app`, minSdk 26.

Core flow: user taps any link anywhere on the device → `LinkInterceptorActivity` shows a chooser
card (edit the URL, save it, or pick a browser) → the chosen browser opens the (possibly edited)
URL directly via an explicit package intent.

## Commands

```sh
./gradlew assembleDebug      # build app/build/outputs/apk/debug/app-debug.apk
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
  release a different signature and break in-place updates.

## Architecture

Manual DI, no framework: `LinkerApplication` owns a single `AppContainer`
(`di/AppContainer.kt`), lazily building the Room `AppDatabase` and three repositories. Compose
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
  every exit path (pick a browser, cancel, jump to Manage Browsers) calls `finish()`. Deliberately
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

**No link preview**: the chooser only shows the domain (parsed from the URL) plus an editable text
field — it deliberately doesn't render the destination page. There's no `INTERNET` permission in
the manifest as a result; opening a link never needs one, since that always hands off to the
chosen browser's own process via an explicit-package `Intent`. The URL field itself is multi-line
with no line cap and a smaller-than-body text style specifically so a long URL is fully visible by
wrapping, rather than being truncated or requiring horizontal scrolling.

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

Dialog action buttons are colored per role rather than left as identical default-primary
`TextButton`s (`ManageBrowsersScreen.kt`'s `RenameBrowserDialog`): `Save` keeps `primary` (the
emphasized action), `Cancel` is `onSurfaceVariant` (neutral), `Reset` is `error` (a mild "this
undoes something" signal, since it discards the custom name back to the system one).

## Fonts

Martian Mono Nerd Font ships as bundled `.ttf`s under `res/font/` (license in
`MARTIAN_MONO_LICENSE.txt` at repo root), applied via `ui/theme/Type.kt` exactly like
ownscreen/noter.
