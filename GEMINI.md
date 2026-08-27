# Antigravity Workspace Guidelines & Rules

## Core Rules

- **Modular Components**: Keep components small, focused, and modular.
- **Database Safety**: Always include robust error handling (try-catch / Result) in database calls and repository operations.

## Modes of Operation

- **Explain Mode**: When asked to investigate or explain, summarize clearly before writing or proposing code changes.
- **Implement Mode**: Only write code after a clear plan or direction is agreed upon.

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

## Response Style

- Keep responses concise and technical. Skip filler like "Great question!".
- Use code blocks for all code snippets, even short ones.
- Prefer bullet points over paragraphs for technical explanations.
- When referencing files, always use clickable markdown links.
