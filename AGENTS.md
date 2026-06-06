# AGENTS.md

## Project

Flowtone is an Android music player written in Kotlin and Jetpack Compose.

## Language

- User-facing text should be Simplified Chinese.
- Code comments may use Chinese when helpful.

## Architecture

- Use Kotlin.
- Use Jetpack Compose.
- Use Material Design 3.
- Use MVVM.
- Keep UI, playback, data scanning, and permission handling separated.
- Do not put ExoPlayer logic directly inside Composable functions.
- Prefer small files and clear package structure.

## Package layout

Suggested structure:

- ui/
- ui/screens/
- ui/components/
- playback/
- data/
- model/
- permissions/

## Playback

- Use AndroidX Media3 ExoPlayer.
- Playback state should be exposed through ViewModel state.
- MiniPlayer and NowPlaying screen should share the same playback state.

## Milestone 0.1

The first milestone should only implement:

- Request audio permission.
- Scan local audio files.
- Display local songs in a list.
- Tap a song to play it.
- Play / pause from a mini player.
- Show current song info.

Do not implement online music services, account system, lyrics, plugin system, or complex playlist features before local playback works.

## Rules for Codex

- Make a plan before large changes.
- Keep the app buildable after each milestone.
- Run Gradle build after code changes when possible.
- Avoid over-engineering.
- 
## Maintainability

This project must be maintainable by the project owner, who is still learning Android development.

Rules:
- Prefer simple, explicit code over clever abstractions.
- Do not introduce complex architecture unless clearly necessary.
- After adding a new file or concept, explain why it exists.
- Keep each file small and focused.
- Avoid dependency injection frameworks for now.
- Avoid advanced patterns before the basic player works.
- Every implementation step should be understandable to a beginner-intermediate Android learner.