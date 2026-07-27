# Linker

[![Build APK](https://github.com/jehan593/linker/actions/workflows/build-apk.yml/badge.svg)](https://github.com/jehan593/linker/actions/workflows/build-apk.yml)
[![Latest release](https://img.shields.io/github/v/release/jehan593/linker)](https://github.com/jehan593/linker/releases/latest)

Linker makes itself the default handler for `http`/`https` links on Android and shows its own
chooser instead of opening a browser directly — edit the URL, save it for later, or send it
straight to a browser of your choice.

## Features

- **Link chooser** — every tapped link opens a small card first: edit the URL, save it, send it
  to Notesnook, or pick which installed browser opens it.
- **Manage browsers** — hide, rename, and drag-to-reorder the browsers offered in the chooser.
- **Saved links** — a searchable, day-grouped history of saved links, each editable, re-openable,
  or deletable individually.
- **Send to Notesnook** — send any link straight to your Notesnook inbox.
- **Nord theme** — dark/light color schemes built on the [Nord](https://www.nordtheme.com/)
  palette, with Martian Mono Nerd Font throughout.

## Install

Grab the latest APK from [Releases](https://github.com/jehan593/linker/releases/latest). After
installing, set Linker as your default browser app (Settings → Apps → Default apps → Browser app)
to have it intercept links tapped elsewhere on the device.

## Building from source

```sh
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk (minified, resource-shrunk)
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

Requires an Android SDK referenced via `local.properties` (`sdk.dir=...`). `compileSdk` 35,
`minSdk` 26.

## Tech stack

Kotlin, Jetpack Compose, Material 3, Room. Manual dependency injection (no DI framework) — see
`CLAUDE.md` for the full architecture writeup.

## License

App source is unlicensed (all rights reserved). The bundled Martian Mono Nerd Font is licensed
separately under the SIL Open Font License 1.1 — see
[`app/licenses/MARTIAN_MONO_LICENSE.txt`](app/licenses/MARTIAN_MONO_LICENSE.txt).
