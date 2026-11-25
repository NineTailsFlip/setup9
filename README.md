# Setup

The first-time setup of [NineTailsFlip](https://github.com/NineTailsFlip)'s LineageOS build for keypad
flip phones (ALT AT-M140, thor2), in the look of the Nine apps: lists, a hint bar, and every
step reachable with the D-pad, OK, Back and `*`.

| Step | What it sets |
|---|---|
| Language | the system language (the Nine apps themselves stay in English) |
| Wi-Fi | turns Wi-Fi on, lists networks and joins one; a password is typed below the list |
| Date and time | automatic date, time and time zone, or each by hand; time zones west to east |
| Screen lock | a PIN of 4 to 16 digits, typed twice, or none |
| The Nine apps | font (monospace or proportional) and hint bar for every Nine app at once |
| All set | a summary, then the home screen |

Keys: Up/Down move, OK chooses, Left/Right change a value (and jump by letter or time zone
offset in long lists), Back is the previous step, `*` the menu (skip a step).

## How it fits in

- **In the image, not on its own.** The ROM build ([android_vendor_thor2](https://github.com/kloploid/android_vendor_thor2))
  takes the latest release, re-signs it with the platform key and installs it as a privileged app
  in place of LineageSetupWizard. Until setup is done it's the home screen; finishing marks the
  phone set up and disables it.
- **The look reaches every Nine app** through nine-core's `SetupChoices`: Setup serves the font
  and hint bar from a read-only provider, and each app adopts them once, at its next start, into
  its own Settings → Theme (which can change them again afterwards).
- System calls go through one interface, `Device`; `AndroidDevice` uses what Settings and
  LineageSetupWizard use (`LocalePicker`, `LockPatternUtils`, `WifiManager` with
  `NETWORK_SETTINGS`), which a platform-signed app may.

## Build

```bash
git clone --recursive https://github.com/NineTailsFlip/setup9
cd setup9 && ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Releases: a tag `v<versionName>` builds and publishes it (NineTailsFlip/.github's `android.yml`).

GPL-3.0, like the rest of the Nine apps.
