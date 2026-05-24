# Readr

Readr is an Android document reader that turns PDFs and other document formats into narrated, resumable reading sessions. It combines document extraction, live highlighting, local or cloud text-to-speech, AI summaries, and optional Story Mode ambience.

## Features

- Open PDF, EPUB, Word, text, HTML, XML, and comic archive documents from Android storage.
- Resume documents from saved reading progress.
- Double-tap a word or paragraph area to start narration near that point.
- On-device TTS support with voice, language, and speed controls.
- Rumik cloud TTS support with configurable voice profile:
  - gender
  - age range
  - regional accent
- Queued cloud narration that requests the next chunk before the current audio finishes.
- Cloud websocket retry for transient audio stream failures.
- Research Paper Mode for wide or multi-column technical PDFs.
- Story Mode, which searches Freesound for cue words in the current text and plays a 10-second background preview at 60% volume with fade-out.
- OpenRouter-powered document summaries, with summary playback.

## Project Structure

```text
Readr/
├── app/          Main Android app, Compose UI, navigation, onboarding, summaries
├── core-domain/  Shared domain models, repository interfaces, playback state
├── core-data/    Room database and persistence repositories
├── core-ui/      Shared Compose theme and UI primitives
├── pdf/          Document opening, PDF rendering, text/layout extraction
├── playback/     Narration playback controller and utterance chunking
├── storage/      Android storage/document descriptor integration
└── tts/          Android TTS, Rumik cloud TTS, Freesound Story Mode support
```

## Requirements

- Android Studio
- JDK 17
- Android SDK with compile SDK 36
- Gradle wrapper included in the repository

Minimum Android SDK: 26.

## API Keys

Readr can run with local/on-device narration without cloud keys, but cloud features need keys.

Add keys to `local.properties`, `.env`, or environment variables:

```properties
RUMIK_API_KEY=your_rumik_key
OPENROUTER_API_KEY=your_openrouter_key
FREESOUND_API_TOKEN=your_freesound_token
```

Accepted aliases:

- `SILK_API_KEY` can be used instead of `RUMIK_API_KEY`.
- `OPEN_ROUTER_API_KEY` can be used instead of `OPENROUTER_API_KEY`.

`FREESOUND_API_TOKEN` has a development fallback currently configured in the TTS module, but using your own token is recommended.

## Build

Compile the debug Kotlin sources:

```bash
./gradlew :app:compileDebugKotlin
```

Build the debug APK:

```bash
./gradlew :app:assembleDebug
```

Run unit tests:

```bash
./gradlew testDebugUnitTest
```

Run Android lint:

```bash
./gradlew lintDebug
```

## Cloud TTS

Cloud narration uses Rumik over websocket. Readr sends paragraph-sized chunks and queues the next chunk shortly before the current audio ends to reduce gaps. If the websocket fails before audio completes, the app retries with a fresh websocket session for the same text.

Voice descriptions are generated from onboarding preferences:

```text
a <gender> voice in <age> with <region> accent and casual speaker with medium intensity at conversational pace
```

## Story Mode

Story Mode is available from the reader controls. When enabled, Readr scans the current narration text for configured cue words, searches Freesound, downloads the first result's `preview-hq-mp3`, caches it on-device, and plays 10 seconds under the narration with fade-out.

The Freesound cache is stored in the app cache directory under:

```text
freesound-story/
```

## Notes

- The project uses Jetpack Compose, Hilt, Kotlin coroutines, Room, OkHttp, and PDFBox Android.
- Generated build outputs are not required for source changes and should generally stay out of commits.
- Cloud narration and Story Mode require network access.
