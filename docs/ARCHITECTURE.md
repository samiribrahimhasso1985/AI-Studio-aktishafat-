# Architecture

## Layers (clean architecture, MVVM)

```
ui/            Compose screens + ViewModels (Library, Editor, Prompter, Studio, Settings)
  prompter/    PrompterController (state holder), PrompterSurface (renderer), PrompterRuntime (wiring)
platform/      Android services behind small classes
  speech/      VoiceTracker (SpeechRecognizer, auto-restart, on-device first), NarrationPlayer (TextToSpeech)
  remote/      RemoteControlBus (Bluetooth key events → commands)
  security/    SecretCipher (Android Keystore AES-GCM)
data/          Room database, DataStore settings, document importer (SAF, PdfBox), cloud AI client
domain/        Pure Kotlin, no Android imports — unit-tested on the JVM
  engine/      ScrollEngine (frame-based state machine: countdown, play, pause, follow-target)
  voice/       VoiceAligner (word alignment), SpeechPaceTracker (wpm, pause detection)
  text/        TextNormalizer (Arabic/German/English), Paragraphs, PronunciationDictionary
  assistant/   LocalScriptAssistant (offline), SpeechDurationEstimator
  importer/    TXT (encoding detection incl. Windows-1256), Markdown, DOCX
  remote/      RemoteKeyMapper
  model/, repository/   Models and repository interfaces
```

Dependencies point inward: `ui → platform/data → domain`. A small `AppContainer` (manual DI) creates the
singletons.

## Key design decisions

- **Smooth scrolling.** The script column is moved with a graphics-layer translation driven by
  `withFrameNanos`, so scrolling is sub-pixel smooth at 60/120 Hz and independent of `ScrollState`.
  Frame times are fractional milliseconds (an earlier integer version drifted 4 % at 60 Hz — caught by tests).
- **Voice tracking.** Android's recognizer ends sessions after silence, so `VoiceTracker` restarts it
  continuously and prefers the on-device recognizer (falls back to the default service when the language
  model is missing). Every partial hypothesis goes to `VoiceAligner`, which anchors the last spoken word in a
  window ahead of the current position, tolerates misrecognised/skipped words and needs strong evidence to move
  backwards. `SpeechPaceTracker` measures words per minute and detects pauses; between recognizer callbacks
  the prompter glides at the measured pace and stops during pauses. If recognition is unavailable or the mic
  permission is denied, the prompter falls back to timed scrolling automatically.
- **Arabic.** Matching strips diacritics and tatweel, unifies alef/hamza/ya/ta-marbuta variants and Arabic-Indic
  digits. Rendering uses `TextDirection.Content` and an RTL layout direction for Arabic scripts, so mixed
  Arabic/Latin paragraphs and punctuation are laid out by Android's bidi engine. Highlighting uses only colour
  spans, which never break Arabic letter shaping.
- **Stage directions.** `[PAUSE]`, `[وقفة]`, `[BREATH]` and `## Scene` lines are shown but excluded from voice
  matching and from narration.
- **Offline first.** Everything except the optional cloud assistant works without internet. The cloud API key is
  encrypted with an Android Keystore key.
- **Camera Studio.** CameraX `LifecycleCameraController` with `VideoCapture`; recordings go to
  `Movies/Iktishafat` via MediaStore. The prompter overlay is drawn by the app on top of the preview (it is not
  part of the recorded video).

## Plan for later modules (from the specifications)

New modules should be separate Gradle modules (`:core:domain`, `:feature:livestudio`, `:feature:fx3`, …) once
the app grows beyond phase 1; phase 1 keeps one `:app` module with strict package layering so the split is
mechanical. Hardware-dependent modules (UVC capture for the Sony FX3 via HDMI→USB-C capture cards, Sony
remote control) need real hardware and are tracked as blocked in `STATUS.md`.
