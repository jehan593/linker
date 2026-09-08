# Linker

[![Build APK](https://github.com/jehan593/linker/actions/workflows/build-apk.yml/badge.svg)](https://github.com/jehan593/linker/actions/workflows/build-apk.yml)
[![Latest release](https://img.shields.io/github/v/release/jehan593/linker)](https://github.com/jehan593/linker/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Linker catches the links you tap on Android and shows a small chooser first: fix the URL, save it
for later, or send it straight to the browser you pick.

> FYI: this project is fully vibe coded

## Features

- **Link chooser** — tap any link and get a small card to edit, save, copy, share, or send it, then pick a browser.
- **Manage browsers** — hide, rename, and reorder the browsers in the chooser.
- **Saved links** — a searchable history of saved links, grouped by day, that you can open, edit, copy, share, or delete.
- **Send to Notesnook** — push any link to your Notesnook inbox.
- **Nord theme** — clean dark/light look with the Nord palette and Martian Mono font.

## Install

Download the latest APK from [Releases](https://github.com/jehan593/linker/releases/latest). After
installing, set Linker as your default browser app (Settings → Apps → Default apps → Browser app)
so links tapped anywhere open this chooser first.

## Build

```sh
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Requires an Android SDK path in `local.properties` (`sdk.dir=...`).

## Tech stack

Kotlin, Jetpack Compose, Material 3, Room.

## License

MIT — see [`LICENSE`](LICENSE). The bundled Martian Mono font is licensed separately under the
SIL Open Font License 1.1 — see
[`app/licenses/MARTIAN_MONO_LICENSE.txt`](app/licenses/MARTIAN_MONO_LICENSE.txt).
