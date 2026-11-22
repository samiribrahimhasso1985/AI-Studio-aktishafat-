# Feature status · حالة الميزات

Last update: 2026-11-22 · Version 1.0.0 (phase 1)

| Mark | Meaning |
|---|---|
| ✅ | Implemented **and** verified by automated tests that were actually run |
| 🟡 | Implemented in code; waiting for the first Android build and a test on the Galaxy Tab S7 Ultra |
| ⏳ | Planned for a later phase (not started — no placeholder buttons exist in the app) |
| ⛔ | Blocked: needs hardware, an SDK or research before it can be built honestly |

**Verification so far:** the pure-Kotlin engine (32 unit tests) was compiled and run with Kotlin 2.0.21 on a
JVM. The Android parts could not be compiled in the build environment because Google's Maven repository was
not reachable from it; they compile in Android Studio or GitHub Actions (`.github/workflows/android.yml`), which
also runs the instrumented tests. Until that first build has passed, every 🟡 item may still need small fixes.

## Phase 1 — this delivery

### Script management
| Feature | Status |
|---|---|
| Create, edit, delete, duplicate, pin, search scripts (Room) | 🟡 (DAO test written: `ScriptDaoTest`) |
| Autosave (700 ms after typing, and when leaving the editor) | 🟡 |
| Import TXT (UTF-8/16, Windows-1256 Arabic, Windows-1252) | ✅ |
| Import Markdown (headings → scenes, code removed) | ✅ |
| Import DOCX (paragraphs, headings, tabs) | ✅ |
| Import PDF (PdfBox-Android) | 🟡 — Arabic PDFs may extract in visual order depending on how the PDF was made |
| "Open with / Share to" the app | 🟡 |
| Arabic, German, English scripts; automatic language detection | ✅ |
| Last reading position per script | 🟡 |
| Folders, version history, export, bookmarks | ⏳ phase 2 (a `folder` field already exists in the database) |

### Teleprompter
| Feature | Status |
|---|---|
| Scroll engine: speed, countdown, pause/resume/restart, seek, end detection | ✅ |
| Smooth sub-pixel scrolling at 60/120 Hz | 🟡 (timing logic ✅) |
| Font size, line and paragraph spacing, margins, text colour | 🟡 |
| Mirror horizontal / vertical, fullscreen (immersive), background opacity | 🟡 |
| Reading line with adjustable position | 🟡 |
| Tap to play/pause, drag to move | 🟡 |
| Pause / breath markers and scene headings shown but not spoken | ✅ |
| Remaining time | 🟡 |

### Voice tracking
| Feature | Status |
|---|---|
| Word alignment (partial results, misrecognitions, skipped words, no jumping back on weak evidence) | ✅ English, German, Arabic |
| Arabic normalisation (diacritics, alef/hamza/ya/ta-marbuta, Arabic-Indic digits) | ✅ |
| Pace measurement (words/min) and pause detection | ✅ |
| Continuous SpeechRecognizer with restart, on-device preferred | 🟡 |
| Automatic fallback to timed scrolling (no permission / unavailable / errors) | 🟡 |
| Resync after manual drag or remote jump | 🟡 |
| Current word highlight, spoken text dimmed | 🟡 |
| Iraqi dialect recognition | ⛔ not evaluated — depends on the recognizer installed on the device; needs a real test |
| Voice commands ("faster", "next scene" …) | ⏳ phase 2 |

### Arabic language system (mandatory module)
| Feature | Status |
|---|---|
| Full RTL UI, Arabic interface strings (130 strings in ar/de/en, none missing) | 🟡 (completeness ✅) |
| Interface language independent of script language | 🟡 |
| Mixed Arabic/Latin text, diacritics display, punctuation (Android bidi engine) | 🟡 — must be checked visually on the device |
| Arabic text-to-speech: voice choice, speed, pitch, paragraph reading, pause/resume, prompter follows the voice | 🟡 |
| Pronunciation dictionary (whole words, diacritic-insensitive, longest match wins) | ✅ |
| Iraqi Arabic TTS voice | ⛔ only if the TTS engine provides `ar-IQ`; not claimed |
| Custom Arabic fonts (Cairo, IBM Plex Sans Arabic) | ⏳ phase 2 (system Arabic font used now) |
| Arabic subtitles (SRT/VTT), live graphics | ⏳ phases 4–5 |

### Camera Studio
| Feature | Status |
|---|---|
| Live preview, front/rear switch, torch | 🟡 |
| Video recording to Movies/Iktishafat, with/without audio | 🟡 |
| Quality 720p / 1080p / 4K (with automatic fallback) | 🟡 — 4K depends on the device camera |
| Pre-roll countdown, recording timer, free-storage indicator | 🟡 |
| Prompter overlay near the front lens, adjustable opacity | 🟡 |
| Voice tracking while recording | 🟡 — some devices cannot share the microphone; the app warns |
| Focus/exposure controls | ⏳ phase 2 |

### Bluetooth remote / keyboard
| Feature | Status |
|---|---|
| Key mapping: keyboards, clickers, pedals, media and selfie remotes | ✅ |
| Routing only while a prompter is open; no repeated toggles when holding a key | 🟡 |

### AI writing assistant (optional)
| Feature | Status |
|---|---|
| Offline: improve for speaking, add pauses, divide into scenes, duration estimate | ✅ |
| Preview before applying, one-step undo | 🟡 |
| Optional cloud AI with the user's own Anthropic key (encrypted with Android Keystore) | 🟡 — needs a key to test |

## Later phases (from the specifications)

| Module | Phase | Status / note |
|---|---|---|
| Dashboard, projects, workspaces (Creator / Studio / Live), scene timeline | 2 | ⏳ |
| Audio level meter, clipping, battery/temperature monitor | 2 | ⏳ |
| AI Presenter (delivery suggestions), AI Director, Storytelling assistant | 3 | ⏳ |
| Pronunciation coach (record and compare attempts) | 3 | ⏳ |
| Eye-contact and gesture control (ML Kit / MediaPipe, on-device) | 3 | ⏳ |
| Documentary, Podcast, Interview studios, multi-person mode, B-roll library, green screen | 4 | ⏳ |
| Subtitles (SRT/VTT), translation, presenter analytics, thumbnail studio | 5 | ⏳ |
| DaVinci Resolve export (SRT, CSV, FCPXML/EDL) | 5 | ⏳ — each format must be validated by importing into DaVinci Resolve |
| Remote studio control over local network, cloud backup, offline AI models | 6 | ⏳ |
| **Sony FX3 HDMI preview via UVC capture card** | FX3 spec | ⛔ needs the capture card and FX3 for testing; Android UVC support varies by device and requires a UVC library |
| **Sony FX3 remote control** (record, ISO, shutter, WB) | FX3 spec | ⛔ research needed: Sony's Camera Remote SDK targets desktop OSes; FX3 support on Android must be verified before any control is shown |
| **Live Studio** (switcher, RTMP/YouTube streaming, WebRTC guests, instant replay) | Live spec | ⏳ separate module; multi-stream decoding must be measured on the tablet |
| AI Auto Editor, continuity checker, smart B-roll finder | Advanced spec | ⏳ |

## Known limitations
- Android's speech recognizer may play a short sound when it restarts on some devices.
- Recognition quality for Arabic depends on the installed recognizer (Google's on-device models are best).
- The prompter overlay in the studio is drawn by the app; it is not recorded into the video.
