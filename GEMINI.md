# Antigravity Workspace Guidelines & Rules

## Automatic Local Git Commits

- **Automatic Local Commit**: Whenever you complete a feature, bug fix, refactor, or UI modification requested by the user and verify that it compiles/passes tests, **automatically stage and commit the changes to local git** (`git add .` and `git commit -m "<type>: <concise description>"`) before concluding your response.
- **Local vs Remote**: Always commit **locally**. Do not push to remote (`git push`) unless the user explicitly requests it.

## Jetpack Compose Performance Guardrails

- **Phase-Deferred Progress Reads**: Frequently changing state (such as playback progress, slider position, or audio visualizers) must be read in the Draw or Layout phase (`drawBehind`, `graphicsLayer`, or modifier lambdas) rather than in the Composition phase.
- **Lifecycle Flow Collection**: Always use `collectAsStateWithLifecycle()` instead of `collectAsState()` for ViewModel StateFlows to stop background CPU battery drain.
- **Lazy Lists**: Always provide unique `key` and explicit `contentType` on all `LazyColumn` and `LazyRow` items.

## Modular Architecture Rules

- Keep screen composables separated into their dedicated files:
  - `HomeScreen.kt`, `NowPlayingScreen.kt`, `QueueScreen.kt`, `LibraryScreen.kt`, `PlaylistDetailScreen.kt`, `CommonComponents.kt`, and `UiUtils.kt`.
- Never place entire new screens or large feature sets inside a single monolithic file.

## Background Playback & Media3 Safety

- **Service Continuity**: Never leave ExoPlayer in an unhandled `STATE_ENDED` or idle state that drops the foreground media notification while in the background.
- **Playlist Isolation**: When `playlistQueueActive` is `true`, playback must strictly loop or stay within playlist songs and never inject external radio recommendations.

## Commit Style

- Format local commit messages using Conventional Commits:
  - `feat(ui): ...`, `fix(player): ...`, `perf(compose): ...`, `refactor(nav): ...`

