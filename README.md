# Setup

The first-time setup of NineTailsFlip's LineageOS for keypad flip phones, in the Nine look.

[![Build](https://github.com/NineTailsFlip/setup9/actions/workflows/build.yml/badge.svg)](https://github.com/NineTailsFlip/setup9/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/NineTailsFlip/setup9)](https://github.com/NineTailsFlip/setup9/releases/latest)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

Setup replaces LineageSetupWizard on the [thor2 image](https://github.com/kloploid/android_vendor_thor2)
(ALT AT-M140). Every step is a Nine list — a hint bar at the bottom, everything reachable with
the D-pad, OK, Back and `*`. Part of [NineTailsFlip](https://github.com/NineTailsFlip).

## Features

| Step | What it sets |
|---|---|
| Language | The system language (the Nine apps themselves stay in English) |
| Wi-Fi | Turns Wi-Fi on, lists networks and joins one; the password is typed below the list |
| Keyboard | Which of K9's layouts are on, any number of them (the phone's language is ticked to start) |
| Date and time | Automatic date, time and time zone, or each by hand; time zones west to east |
| Screen lock | A PIN of 4 to 16 digits, typed twice, or none |
| The Nine apps | Font (monospace or proportional) and hint bar for every Nine app at once |
| All set | A summary, then the home screen |

K9 becomes the keyboard on the first screen, so the Wi-Fi password is typed with it.

## Installation

Setup is part of the image and isn't installed on its own: the ROM build takes the latest
release, re-signs it with the platform key and installs it as a privileged app. Until setup is
done it is the home screen; finishing marks the phone set up and disables it.

## Usage

| Key | Does |
|---|---|
| Up / Down | Move |
| OK | Choose |
| Left / Right | Change a value; jump by letter or time-zone offset in long lists |
| Back | The previous step |
| `*` | Menu: skip a step, the previous step |

## How it works

- **System calls** go through one interface, `Device`. `AndroidDevice` uses what Settings and
  LineageSetupWizard use — `LocalePicker`, `LockPatternUtils`, `WifiManager` with
  `NETWORK_SETTINGS` — which a platform-signed app may.
- **The look reaches every Nine app** through nine-core's `SetupChoices`: Setup serves the font
  and hint bar from a read-only provider, and each app adopts them once, at its next start, into
  its own Settings → Theme.
- **Keyboard layouts** are read from and written to K9 through K9's `LanguagesProvider`.

## Building

Requirements: JDK 17+, Android SDK (platform 34).

```bash
git clone --recursive https://github.com/NineTailsFlip/setup9.git
cd setup9
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

A tag `v<versionName>` builds and publishes a release (NineTailsFlip/.github's `android.yml`).

## Project structure

| Path | What's there |
|---|---|
| `app/src/main/java/io/github/ninetailsflip/setup/` | `Wizard` and `Steps` (the steps), `SetupActivity` (the screen), `Device`/`AndroidDevice` (system calls), `Choices`/`ChoicesProvider` (the look for the other apps) |
| `nine-core/` | Git submodule: [nine-core](https://github.com/NineTailsFlip/nine-core) |

## Contributing

Issues and pull requests are welcome; see
[CONTRIBUTING](https://github.com/NineTailsFlip/.github/blob/main/CONTRIBUTING.md).

## License

GPL-3.0 — see [LICENSE](LICENSE), like the rest of the Nine apps.
