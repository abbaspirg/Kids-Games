# Spooky Snake 🎃

A Halloween-themed snake game for Android, written in Kotlin with no third-party libraries.

- Coral snake with a big head, blinking eyes and a fading tail
- Treats to eat: 🎃 pumpkins, 👻 ghosts and 🍬 candy
- Dotted board with cobwebs and a spider dangling on its thread
- The snake wraps through the edges, so the only way to lose is biting yourself
- Gets faster as you eat more; your best score is saved
- On the start screen the snake plays by itself until you tap

## Controls

- **Swipe** up / down / left / right to steer (one long drag can make several turns)
- **Tap** to start, resume after pausing, or play again

## Build

Open the `SnakeGame` folder in Android Studio and press Run, or from the command line:

```bash
cd SnakeGame
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Each push that touches `SnakeGame/` also builds the APKs on GitHub Actions
(workflow **Build Spooky Snake APK**). Download them from the run's **Artifacts** section.

Requirements: JDK 17+, Android SDK 35. Runs on Android 8.0 (API 26) and newer.
