# Screen Translator

**English** | [한국어](README.ko.md)

An Android digital assistant app: long-press the home button and the current screen is sent straight to **Papago image translation** (default), **Google Lens**, or any translator app you add (e.g. DeepL).
It replaces the "translate what's on screen" flow that disappeared when Google Assistant was merged into Gemini.

- Screenshots are stored only in the app's private cache (`cache/shots/`) and never appear in the gallery. Screenshots older than 10 minutes are deleted on the next run.
- Tested on: Galaxy Note 20 (SM-N981N, Android 13)
- Language: Korean on Korean-language phones, English everywhere else. On Android 13+ you can change just this app's language in Settings > General > App languages (the launcher keeps showing the app name in the system language).

## Install and set up

1. Download the latest `screen-translator-vX.Y.Z.apk` from [Releases](https://github.com/dhnam/screen-translator/releases) and install it. You can also build it yourself (see "Build").
2. Open **Screen Translator** and **allow notifications**. One UI blocks toast messages from apps without notification permission.
3. Tap **Open assistant settings**, or go to Settings > Apps > Choose default apps > **Digital assistant app**, and pick **Screen Translator**.
4. On the same screen, turn on both **Use text from screen** and **Use screenshot**.
5. In the app, pick the default from the translator app list. Use **+ Add app** to add any app that accepts images, and ✕ to remove one. Apps that aren't installed are shown greyed out.

## Good to know

- **"This screen can't be captured…"**: the screen blocks screenshots (banking apps, DRM video), or the settings from step 4 are off.
- **Papago opens slowly**: when Papago isn't running (e.g. right after you swipe it away from recent apps), Papago itself takes 3–5 seconds to start. This isn't caused by this app.
- **Voice input**: registering as an assistant requires a speech recognition service, so the app includes an empty one. On the Note 20 the system speech recognizer didn't change, but on some devices voice input may stop working in apps that use the system speech recognizer.
- **Switching back**: pick Google (or any app) again under Digital assistant app.

## Build

Requirements: JDK 17 and the Android SDK (`platforms;android-34`, `build-tools;34.0.0`, `platform-tools`).
Put `sdk.dir=<path to SDK>` in `local.properties`, then run:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Install over wireless debugging:

```bash
adb pair <IP>:<pairing port> <code>
adb connect <IP>:<port>
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Release

Pushing a `v*` tag makes GitHub Actions (`.github/workflows/release.yml`) run the tests, build a signed release APK, and attach it to a Release.

```bash
git tag v0.1.1 && git push origin v0.1.1
```

- The signing key lives in the repository secrets (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`). For a local release build, set the `KEYSTORE_FILE` and `KEYSTORE_PASSWORD` environment variables.
- Release certificate SHA-256: `56:14:E0:DB:F9:DE:BE:DC:BE:17:7E:84:D9:AB:5A:C8:53:A3:90:E7:E9:35:12:2F:57:A2:C7:DE:F2:A0:41:9C`
- Debug builds install as a separate app, "Screen Translator (dev)" (`dev.dhnam.screentranslator.dev`), so they can sit next to the release app. Only one app can be the digital assistant at a time.

## Structure

| File | Role |
|---|---|
| `AssistService` / `AssistSessionService` | Digital assistant registration |
| `StubRecognitionService` | Empty speech recognition service required for assistant registration |
| `AssistSession` | Receives the screenshot, saves it, and shares it to the translator app. Shows a message if no screenshot arrives within 2 seconds |
| `ShotStore` | Saves screenshots and deletes ones older than 10 minutes |
| `TargetList` | Translator app list and default rules (add, remove, set default, migrate v0.1.0 settings) |
| `TargetResolver` / `Prefs` | Finds activities that accept images, builds the share intent (share sheet if there's no target), stores settings |
| `SettingsActivity` | Settings screen |

Design notes (Korean): `docs/superpowers/specs/2026-11-09-screen-translator-design.md`

## License

MIT. See `LICENSE`.
