# Kappu Suppu

A two-player, fully offline Android game inspired by the notebook-and-pencil game many of us played as
children. Two people share one phone. Kappu and Suppu each own five characters. An even page number from a
book decides which character gets to act.

Native Android: Kotlin, Jetpack Compose, MVVM, StateFlow, Navigation Compose. No network permission,
no login, no backend, no saved games (closing the app ends the game).

> **Build status, honestly stated.** The pure-Kotlin game engine, the drawing-data layer and the session
> logic are covered by **96 unit tests, which pass**. The Compose UI, audio player and Gradle/AGP build
> were written against the documented APIs but **have not been compiled by the author of this project
> archive**, because the environment it was produced in had no access to Google's Maven repository. The
> GitHub Actions workflow is the first real compile. If it reports a compile error, see
> [Known limitations](#known-limitations).

## Rules in one minute

* Open a book (a real one, or the in-app simulator) and read the **even** page. The **last digit** picks the
  character: `0 → 1`, `2 → 2`, `4 → 3`, `6 → 4`, `8 → 5`. Example: `126 | 127` → use 126 → Character 4.
* A matching digit adds one drawing stage, on top of the existing pencil drawing:
  1 circle → 2 eyes + mouth → 3 stick body, arms, legs → 4 gun beside it → 5 red bullet in the gun.
* A character at stage 4 that gets its digit **loads** the bullet (stage 5). A character at stage 5 that gets
  its digit **may shoot**: the shooter manually picks any living enemy character and confirms.
* After shooting, the bullet is gone (stage 4), the shooter lives on, and it can reload later. Unlimited.
* **Defence:** a target with no gun (stage 1–3) dies and is crossed out. A target **with** a gun, loaded or
  not, only loses the gun and bullet and drops to stage 3. It must rebuild: gun first, bullet next.
* Loading never grants an extra turn. Dead characters waste the turn. Kappu and Suppu alternate.
* A player with no living characters is eliminated; the other player wins.

## Project layout

```
app/src/main/java/com/kappusuppu/
  domain/            pure Kotlin, no Android: model/, rules/, actions/, engine/
  drawing/           stroke data and pencil geometry (pure Kotlin): InkBoard, StrokeFactory, Wobble
  data/audio/        GameAudio interface + AndroidGameAudio (MediaPlayer + SoundPool)
  presentation/      Compose UI: home, roles, book, game, target, result, howtoplay, components, theme
app/src/main/res/raw/  bundled audio (4 OGG files)
app/src/test/          unit tests (JUnit 4)
tools/generate_audio.py   regenerates every audio asset from scratch
.github/workflows/android-build.yml
```

How the requirements map onto the code:

| Requirement | Where |
|---|---|
| Character stages, defence, reload, turns, winner | `domain/engine/GameEngine.kt`, `domain/model/Models.kt` |
| Impossible states (stage 1–3 with a gun, dead with a gun …) | Gun/bullet are *derived from the stage*; `GameCharacter` validates in `init` |
| Duplicate-tap protection | every state-changing engine call carries the turn number it was issued for; repeats are rejected |
| Even-page validation, last-digit mapping | `domain/rules/PageRules.kt` |
| 200-page simulator | `domain/engine/BookSimulator.kt` (`nextInt(1, 101) * 2` → 2…200) |
| Cumulative, stroke-by-stroke drawing | `drawing/InkBoard.kt` (persistent strokes, only new ones animate), `presentation/components/CharacterCanvas.kt` |
| Cunning / shocked faces | `StrokeFactory.expressionFace`, shown as a temporary overlay; stored strokes are never changed |
| 6 × 2 notebook board | `presentation/game/NotebookBoard.kt` |
| Target selection, cancel, confirm | `presentation/target/TargetScreen.kt`, `presentation/game/GameSession.kt` |
| Audio, focus and lifecycle | `data/audio/AndroidGameAudio.kt` |

## Open it in Android Studio

1. Install a current Android Studio (one that supports AGP 9.5; see the table below) with JDK 17 or newer.
2. *File → Open…* and choose this folder (`KappuSuppu`, the one containing `settings.gradle.kts`).
3. Let Gradle sync. Android Studio offers to download Android SDK Platform 37 if you do not have it.

## Run it locally

* **On a phone:** enable *Developer options → USB debugging*, connect it, pick it in the device list, press *Run*.
* **On an emulator:** *Device Manager → Create device*, pick an image (API 26 or newer), press *Run*.
* **Command line:** `./gradlew installDebug` (device or emulator connected).

## Run the tests

```
./gradlew testDebugUnitTest
```

The report is written to `app/build/reports/tests/testDebugUnitTest/index.html`. The tests need no emulator.
They cover player setup, physical-book validation, the simulator range, progression, shooting, defence,
reload, recovery, turn management, duplicate taps and cancelled shots, the stroke data (cumulative drawing,
only new strokes animate, red bullet only, gun removal keeps face and body) and the session flow.

## Build the APK

```
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Push to GitHub and get the APK from Actions

1. Create an empty repository on GitHub.
2. In this folder:
   ```
   git init
   git add .
   git commit -m "Kappu Suppu"
   git branch -M main
   git remote add origin https://github.com/<you>/<repo>.git
   git push -u origin main
   ```
3. Open the repository's **Actions** tab. The *Android build* workflow runs on every push and pull request
   (and can be started by hand). It sets up JDK 21 and the Android SDK, restores the Gradle cache, runs the
   unit tests, builds the debug APK and uploads it.
4. Open the finished run and download **kappu-suppu-debug-apk** from *Artifacts* (a zip containing `app-debug.apk`).
   If something failed, the **unit-test-reports** artifact holds the test report and the failing step is
   marked in the log.

## Install the APK on a phone

1. Unzip the artifact and copy `app-debug.apk` to the phone (cable, cloud drive, or email to yourself).
2. Open the file on the phone. When asked, allow *Install unknown apps* for the app you opened it from.
3. Tap *Install*, then open **Kappu Suppu**. (Debug builds are signed with a debug key; Play Protect may ask for confirmation.)

Or, with USB debugging on: `adb install -r app-debug.apk`.

## Toolchain versions and why

| Component | Version | Basis |
|---|---|---|
| Android Gradle Plugin | 9.5.0 | latest stable on the AGP release-notes page when this was written |
| Gradle | 9.6.0 | required minimum for AGP 9.5 (wrapper) |
| compileSdk / targetSdk | 37 (Android 17) | Compose 1.12 targets API 37, which needs AGP ≥ 9.1.1 |
| minSdk | 26 | Android 8.0; gives adaptive icons, `AudioFocusRequest`, and covers virtually all active devices |
| Kotlin | 2.4.10 | version shown in the AGP 9.5 documentation example |
| Compose BOM | 2026.08.00 | August 2026 Compose release (Compose 1.12) |
| JDK | 21 (CI); 17+ locally | build JDK; bytecode target is 17 |
| Kotlin plugin | not applied | AGP 9 compiles Kotlin itself; only the Compose compiler plugin is added |

The AndroidX library versions (activity 1.11.0, lifecycle 2.9.4, navigation 2.9.5, core-ktx 1.17.0) were chosen
as known-published releases, not as the newest ones. They are collected in `gradle/libs.versions.toml` and can
be raised freely.

## Audio

All four sounds are **original works generated by `tools/generate_audio.py`** (pure synthesis: sine waves,
filtered noise and envelopes; nothing sampled, nothing downloaded). They are released under **CC0 1.0**
(public domain). Run `python3 tools/generate_audio.py` (needs `numpy`, `scipy`, `ffmpeg` with libvorbis)
to regenerate them.

| File | Use | Length |
|---|---|---|
| `page_flip.ogg` | book simulator opens | 0.95 s |
| `pencil_scratch.ogg` | loops while strokes are drawn | 1.4 s loop |
| `suspense_loop.ogg` | quiet suspense bed during play | 24 s seamless loop |
| `tension_cue.ogg` | one-shot when a bullet is loaded | 3 s |

Behaviour: one suspense player only (no overlapping copies); the tension cue pauses the loop and the loop
resumes by itself when the cue ends; a loaded gun does not keep tension music running; the sound toggle
mutes everything; the app pauses audio and gives up audio focus in the background and resumes gameplay music in
the foreground; everything stops when the game ends or the player leaves. No shooting or explosion sound exists.

## Known limitations

* **Not yet compiled or run on a device by the author.** See the note at the top. The first run of the
  GitHub Actions workflow (or *Sync* in Android Studio) is the real compile. The most likely things to need a
  touch-up are a Compose API signature or a dependency version that moved; each is a one-line fix in
  `gradle/libs.versions.toml` or the named file.
* **Audio has never been listened to.** The sounds were synthesised and checked only for format and length.
  They are functional but simple; replace the files in `res/raw/` (same names) to taste.
* **Handwriting font.** Text uses Android's built-in cursive family (`FontFamily.Cursive`) so no font file
  has to be bundled and licensed. Its look varies slightly by device. Drop a font into `res/font/` and change `HandFont` in
  `presentation/theme/Theme.kt` for a different hand.
* Portrait only; the 6 × 2 board is sized for phones.
* Visual layout was reasoned about, not inspected on a screen. Spacing on very small or very large phones may need tuning.
* Kappu always takes the first turn.
* The tension cue is triggered when a bullet is *loaded* (stage 4 → 5), as specified, not when a shot is fired.
