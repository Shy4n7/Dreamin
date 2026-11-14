# Playback, Queue & Micro-Experience Fix Plan

> Steps use checkbox (`- [ ]`) syntax for tracking. Finding numbers (#N) refer to the 2026-11-14 audit.

**Goal:** Make the player correct first (no dropped songs, no wrong jumps, no inflated stats), then make next/previous and the queue behave the way users expect, then polish animations, interactions and visual consistency.

**Order rationale:** Each phase leaves the app shippable. Correctness (Phases 0-2) comes before polish because several animation bugs (#9, #10) are symptoms of the playback logic and disappear once that logic is fixed.

**Global constraints**
- All colors via `LocalDreaminColors.current`; no new hex literals in UI.
- Gesture thresholds and offsets in `dp`, converted with `LocalDensity`.
- Animation specs from `DreaminMotion`; haptics from a single `DreaminHaptics` helper (Phase 6).
- Every phase must pass `.\gradlew.bat compileDebugKotlin testDebugUnitTest`.
- Never catch `CancellationException` without rethrowing.

**Path prefix:** `app/src/main/java/com/shyan/dreamin/` (abbreviated below as `…/`).

---

## Phase 0 — Safety net (pure queue logic + tests)

Extract queue math out of the 3,193-line ViewModel so every later fix is testable without a device.

**Files**
- Create: `…/data/queue/QueueState.kt`, `…/data/queue/QueueOps.kt`
- Modify: `…/viewmodel/MusicPlayerViewModel.kt`
- Test: `app/src/test/java/com/shyan/dreamin/data/queue/QueueOpsTest.kt`

**Tasks**
- [ ] Define immutable `QueueState(items: List<QueueEntry>, currentIndex: Int, userQueuedIds, isPlaylist, repeatMode, shuffle, originalOrder)` where `QueueEntry(uid: String, song: Song)` gives duplicates a stable unique id.
- [ ] Track the current position by **index**, not `indexOfFirst { it.id == current.id }` (fixes the root cause of #3 and #21).
- [ ] Move pure functions into `QueueOps`: `next()`, `previous(positionMs)`, `playNext(song)`, `addToQueue(song)`, `remove(uid)`, `move(from, to)`, `shuffleOn()`, `shuffleOff()`, `upcoming()`, `remainingDurationMs(positionMs)`.
- [ ] ViewModel delegates to `QueueOps`; no behavior change yet except the index-based current pointer.

**Tests (write first)**
- [ ] next/previous at start, middle and end for: plain list, playlist, repeat ALL, repeat ONE, shuffle.
- [ ] Removing the current item keeps a valid `currentIndex` and next still goes to the following song.
- [ ] Duplicates: same song twice, current highlight and next resolve by uid.
- [ ] `remainingDurationMs` excludes played items and subtracts current position.

**Done when:** all queue behaviors are covered by JVM unit tests and the app behaves identically.

---

## Phase 1 — Critical playback correctness

**Files:** `…/viewmodel/MusicPlayerViewModel.kt`, `…/service/MusicService.kt`, `…/data/local/PlaybackAnalyticsTracker.kt`

- [ ] **#1 ExoPlayer list invariant.** Prune every item before `currentMediaItemIndex` on *every* transition (AUTO and SEEK), not only AUTO. In `preloadNextMediaItemInQueue`/`syncPreloadedMediaItem`, never remove the item at `currentMediaItemIndex`; only remove indices `> currentMediaItemIndex`.
- [ ] **#2 Shuffle.** Stop setting `controller.shuffleModeEnabled`; the app owns order. Keep ExoPlayer shuffle permanently off.
- [ ] **#3 Failed song skip.** On unrecoverable error: compute next via `QueueOps.next()` *before* removing the failed entry, then play it.
- [ ] **#4 Retry cap.** One automatic re-resolve per song (track `retriedSongUid`); second failure skips to next and surfaces an error snackbar/inline message.
- [ ] **#6 Play counting.** Remove `recordPlay` from `playSong` and `onMediaItemTransition`; record only through `PlaybackAnalyticsTracker` when the scrobble threshold (≥30 s or ≥50%) is met — on track end, skip, or song change.
- [ ] **#7 Format leak.** Make `resolveStreamUrl` side-effect free (return `ResolvedStream(url, source, format)`); only `playSong`/transition for the *current* song writes `currentAudioFormat`/`currentSongStreamSource`.

**Verify on device**
- [ ] Lock-screen / Bluetooth next ×10 in a row: correct song each time, nothing dropped.
- [ ] Shuffle on, 10 songs: every transition gapless (no spinner).
- [ ] Force a bad stream mid-playlist: next queued song plays, no loop.
- [ ] Play 3 songs fully, skip 2 after 5 s: Recently Played / top songs count 3.

---

## Phase 2 — Queue ownership in the service (#5)

Larger refactor; makes background playback independent of the Activity.

> **Status / handoff (2026-11-15):** Phases 0-1 committed on branch `fix/playback-queue-phase-0-1` (`e1dcb98`). Phase 2: 2a-2e done; 2f partially done (ViewModel side); build + 157 unit tests green. Resume by finishing 2f (save on `onTaskRemoved`/`onDestroy`, restore in `MusicService.onCreate`). Not yet verified on a device.
>
> **After 2e:** every next/previous (app buttons via `MediaController.seekToNext/Previous`, lock screen, notification, Bluetooth) goes through `ForwardingPlayer` → `QueueController.skipToNext/skipToPrevious` (350 ms debounce; uses the preloaded item gaplessly when it matches). ExoPlayer error recovery (re-resolve once, then skip, halt after 3 failures in a row) also lives in `QueueController.onPlayerError`. The ViewModel no longer has any next/previous, radio or error logic.
>
> **After 2d:** `PlaybackQueueStore` is the source of truth (all writes on the main thread; `isExtending` flow drives the "fetching up next" UI). Service `QueueController` owns STATE_ENDED auto-advance, pruning played items, gapless preload (re-synced whenever the store's current/upcoming pair changes), radio extension and unplayable-song skips (max 3 in a row). Pure decisions live in `data/queue/QueueTransitions.kt`; radio search in `CatalogRepository.buildUpNext` / `findRadioContinuation`; `service/MediaItemFactory` builds MediaItems for both sides. ViewModel writes queue changes via `updateQueueState` and mirrors the store into `PlayerUiState`. (2e moved user next/previous and `onPlayerError` into the service too.)
>
> **Where things live now (post Phase 1):**
> - Pure queue math: `…/data/queue/QueueOps.kt` (`next`, `previous`, `upcomingIndex(…, isShuffle)`, `playNext`, `addToQueue`, `removeById`, `removeAt`, `move`, `positionForPlay`, `shuffleUpcoming`, `remainingDurationMs`).
> - Queue state: `PlayerUiState.queue` + `currentQueueIndex` (resolved via `resolvedCurrentIndex()` extension inside the ViewModel).
> - Auto-advance: `playerListener.onPlaybackStateChanged(STATE_ENDED)` → `playNext(isAutoEnd = true)`; gapless preload: `preloadNextMediaItemInQueue` / `syncPreloadedMediaItem` / `preloadedIdAfterCurrent` / `clearItemsAfterCurrent`.
> - Stream resolution: `resolveStreamUrl` (JioSaavn via `AudioStreamResolver`, YouTube fallback via `YtMusicFallbackResolver`, YT ids in `youtubeStreamSongIds`).
> - Radio: `fetchUpNext`, `continueWithRadio`, `resolveRadioCandidates`, `fetchJioSaavnRelated` — all depend on the ViewModel's private `searchOnDevice` (~150 lines of JioSaavn JSON parsing).
> - Service → app: `MusicService` `ForwardingPlayer` broadcasts `ACTION_PLAY_NEXT/PREVIOUS` when ExoPlayer has no neighbour item; ViewModel `mediaActionReceiver` handles them.
> - Session persistence: `UserPreferencesDataStore.saveLastSession(song, positionMs)` (single song only).
>
> **Sub-steps (each independently committable):**
> - [x] **2a Shared queue store.** (done: ViewModel mirrors queue state into the store one-way; 2d flips ownership to the service) Create `…/data/queue/PlaybackQueueStore.kt`: process-wide `object` with `MutableStateFlow<QueueSnapshot(items, currentIndex, userQueuedIds, playlistActive, repeatMode, isShuffle)>`. ViewModel writes every queue mutation through it and derives `PlayerUiState.queue/currentQueueIndex` from it. No behavior change; add a unit test that mutations round-trip.
> - [x] **2b Extract search/radio repository.** (done: `CatalogRepository(api)` class, ViewModel uses `catalog.*`) Move `searchOnDevice`, `searchAlbumsOnDevice`, `fetchJioSaavnRelated`, `resolveRadioCandidates`, `cleanArtworkUrl`, `unescapeHtml` into `…/data/repository/CatalogRepository.kt` (constructor-injected `OkHttpClient`, `ApiService`). ViewModel delegates. Keep existing tests green.
> - [x] **2c Stream resolver service-side.** (done: `service/StreamResolver(downloadRepo, api)`, `StreamResolver.isYoutubeStream(id)`) Move `resolveStreamUrl` + `youtubeStreamSongIds` into `…/service/StreamResolver.kt` usable from both service and ViewModel.
> - [x] **2d QueueController in service.** (done: store owns the queue; see "After 2d" above) `…/service/QueueController.kt` owns: STATE_ENDED auto-advance, preload of next item, radio extension when ≤2 remain (via `CatalogRepository`), using `PlaybackQueueStore` + `QueueOps`. Register as a `Player.Listener` in `MusicService.onCreate`. Remove the ViewModel's STATE_ENDED handling and preload code.
> - [x] **2e Direct controls.** (done; also moved error recovery into `QueueController`) `ForwardingPlayer.seekToNext/Previous` call `QueueController` directly; delete `ACTION_PLAY_NEXT/PREVIOUS` broadcasts and the receiver branches.
> - [~] **2f Persistence.** (partial: queue snapshot saved to DataStore debounced 2 s via `startQueuePersistence`; `resumeLastSession` restores the full queue + index. Still TODO: save on `onTaskRemoved`/`onDestroy` and restore inside `MusicService.onCreate` once 2d exists) Save `QueueSnapshot` + position to DataStore (JSON via Gson, debounce 2 s, also on `onTaskRemoved`/`onDestroy`); restore in `MusicService.onCreate` and in the ViewModel on cold start (replaces single-song `lastSession` resume).
>
> **Verify after 2d-2f:** swipe app from recents mid-playlist → ≥3 songs continue; lock-screen next/previous work with app killed; reopening shows the same queue and position; existing 128 tests + new store/controller tests pass.

**Files:** Create `…/service/QueueController.kt`; modify `MusicService.kt`, `MusicPlayerViewModel.kt`

- [ ] Move `QueueState` + auto-advance (`STATE_ENDED` handling, preload of index+1, radio extension trigger) into a service-scoped `QueueController`.
- [ ] Expose queue to the UI via a custom session command / `MediaSession` extras or a shared `StateFlow` singleton; ViewModel becomes a thin client.
- [ ] Replace broadcast `ACTION_PLAY_NEXT/PREVIOUS` with direct `QueueController` calls inside `ForwardingPlayer`.
- [ ] Persist queue + index + position to DataStore so process death restores the queue, not just the last song.

**Verify:** swipe app from recents mid-playlist → playback continues through ≥3 songs; notification next/previous work; reopening the app shows the correct queue.

---

## Phase 3 — Next / previous semantics

**Files:** `MusicPlayerViewModel.kt` (or `QueueController.kt` after Phase 2), `NowPlayingScreen.kt`

- [ ] **#8 Instant manual skip.** If ExoPlayer index+1 holds the target song, call `seekToNextMediaItem()` instead of `setMediaItem`; fall back to resolve only when not preloaded. Same for tapping the next song in the queue.
- [ ] **#11 Repeat ONE.** Manual next/previous always changes track; repeat ONE only applies to natural track end.
- [ ] **#12 Repeat semantics for playlists.** Remove the implicit `|| playlistQueueActive` looping; playlist end with repeat OFF continues into radio (or stops, product decision). Previous at index 0 restarts the song instead of wrapping.
- [ ] **#13 Shuffle off restores order.** Keep `originalOrder`; shuffle-off rebuilds upcoming from it. User-queued ("Play next") entries stay pinned right after current when shuffling.
- [ ] Repeat cycle order to OFF → ALL → ONE (platform convention).
- [ ] **#14 Resume position.** Use `setMediaItem(item, startPositionMs)` for last-session resume; delete `pendingResumePositionMs`.
- [ ] Expose a one-shot `skipEvents: SharedFlow<SkipEvent(direction, changedTrack: Boolean)>` from the ViewModel for the UI (consumed in Phase 5).

**Tests:** extend `QueueOpsTest` for repeat/shuffle-off/pinned items; device check that manual next on a preloaded song shows no spinner.

---

## Phase 4 — Queue screen

**Files:** `…/ui/screens/QueueScreen.kt`, `NowPlayingScreen.kt`, `MusicPlayerScreen.kt`

- [ ] **#16 Sections.** Render "History" (collapsed, dimmed), a pinned "Now Playing" row, "Next from you" (user-queued), and "Next up" (playlist / radio). Auto-scroll to Now Playing on open.
- [ ] **#15 Header math.** Count and total from `QueueOps.upcoming()` and `remainingDurationMs(position)`; use `formatDuration`; pluralize ("1 song").
- [ ] **#17** Tapping the current row toggles play/pause instead of restarting.
- [ ] **#18 Wire existing features:** Save queue as playlist (menu entry → `SaveQueueDialog`), Clear upcoming, undo snackbar on remove (`onRestoreToQueue`). Delete the Lock/Radio stub parameters.
- [ ] Disallow "Remove from queue" on the current row.
- [ ] **#19 Reorder.** Commit reorder to the ViewModel only on drag end (local list state during drag); add edge auto-scroll while dragging; keys = `QueueEntry.uid`.
- [ ] **#20 Pull-to-close.** Remove the custom `onBack` nested-scroll trigger; let the `VerticalPager` follow the finger (overscroll at list top passes to pager).
- [ ] **#21** Highlight by uid, not song id.

**Verify:** queue of 30 with current at #20 opens scrolled to it; drag a row across 8 positions → one ViewModel update, smooth edge scroll.

---

## Phase 5 — Animation micro-polish

**Files:** `NowPlayingScreen.kt`, `CommonComponents.kt`, `FluidMeshGradientBackground.kt`, `SyncedLyricsView.kt`, `MusicPlayerViewModel.kt`

- [ ] **#9 / #10 Skip animation driven by events.** Artwork animation reacts to `SkipEvent`: slide direction from the event, no slide when `changedTrack == false` (restart / debounced). Remove the `artworkOffsetX == 0f` LaunchedEffect heuristic.
- [ ] **#22 Seek snap-back.** Poll position while paused too (low rate), and keep showing `seekProgress` until the reported position is within 500 ms of the target.
- [ ] **#23 Pause/resume continuity.** Replace infinite transitions whose target flips on `isPlaying` (waveform phase, thumb halo, artwork aura, mini glow) with the decelerating-clock pattern already used by the mesh: a phase accumulator scaled by an animated speed 0→1.
- [ ] **#24 Frame-loop leak.** Read `isPlaying` via `rememberUpdatedState` in `MiniPlayer` and `FluidMeshGradientBackground` loops.
- [ ] **#25 Dismiss jump.** Do not snap `swipeOffsetY` to 0 before exit; animate from the current offset to full height, then reset after the sheet is gone.
- [ ] **#26 Swipe physics.** Use `VelocityTracker`: skip when `|offset| > 72.dp` or `|velocity| > 900.dp/s`; follow the finger with rubber-banding past the threshold instead of a hard clamp.
- [ ] **#27 Single artwork/color update.** Resolve the official poster before committing the transition when it is cached or resolves within ~150 ms; otherwise update once with crossfade. Debounce `extractDominantColor` per song.
- [ ] **#28 Lyrics.** Keep font size constant; animate only scale + alpha (no layout reflow). Pause autoscroll for 3 s after user scroll, show a "Sync" chip to resume. Load lyrics once per song and only when the lyrics view is open or about to open.
- [ ] **#29** Convert every raw-pixel threshold/offset in NowPlaying, Queue, PlaylistDetail and Lyrics to `dp`.
- [ ] Add a shared-element transition (`SharedTransitionLayout`) for mini-player artwork → Now Playing artwork.

**Verify:** record screen at 120 Hz: pause/resume shows no jumps; flick-skip works with a short fast swipe; dismiss from a 200 dp drag has no upward jump.

---

## Phase 6 — Interaction bugs

**Files:** `MusicPlayerScreen.kt`, `NowPlayingScreen.kt`, `ArtistDetailScreen.kt`, `CommonComponents.kt`, `LibraryScreen.kt`, `MusicService.kt`

- [ ] **#30 Artist from Now Playing.** Give the artist overlay `zIndex(3.5f)` and collapse Now Playing (or raise artist above it) on artist tap; add `BackHandler` to `ArtistDetailScreen`.
- [ ] **#31** Double-tap artwork only adds to favorites (never removes); burst still shows.
- [ ] **#32 Sleep timer.**
  - Live countdown text (ticker every 30 s via `produceState`).
  - Service notifies expiry via session extras; ViewModel clears `sleepTimerEndMs`.
  - Tap on active timer opens the dialog instead of cancelling.
  - "End of song" = pause on `STATE_ENDED`/transition of the current track, not a minute estimate.
  - Fade with 30+ steps on an exponential curve.
- [ ] **#33** Play/pause button stays enabled while buffering (pause allowed); show spinner as a ring around the icon instead of replacing it.
- [ ] **#34** Wire "Create new playlist" in `AddToPlaylistDialog` to `CreatePlaylistDialog` then add the song; remove the duplicate "+".
- [ ] **#35** Align copy: "Add to queue" = after current + user block; rename sheet item and subtitle accordingly.
- [ ] **#36** Library favorites/downloads/recent use `playSongFromList(song, list)` so context continues (offline-safe for downloads).
- [ ] **#37** Global snackbar host in `MainAppScaffold` (above mini player); confirmations for add to queue, play next, add to playlist (with "Already in playlist" case), remove with Undo.
- [ ] Notification shuffle button: proper shuffle icon with on/off states.

---

## Phase 7 — Design-system consistency

**Files:** `…/ui/screens/UiUtils.kt`, `…/ui/theme/Theme.kt`, `…/ui/theme/Type.kt`, all screens/components

- [ ] Single color source: delete duplicate palette in `theme/Theme.kt` (or `UiUtils.kt`), map `ResonanceTheme` to `DreaminColors`. Replace `Color(0xFFA8C7FA)` etc. in Sleep Timer and others with tokens.
- [ ] Motion tokens: extend `DreaminMotion` (`Quick`, `Standard`, `Emphasized`, `Bouncy`, enter/exit durations) and replace ad-hoc `spring`/`tween` (≈60 sites, 9 damping ratios).
- [ ] `DreaminHaptics`: `tap` (ContextClick/Confirm), `toggle`, `tick` (SegmentTick/TextHandleMove), `longPress`; replace the 17 `LongPress` uses.
- [ ] Typography: map the 26 ad-hoc font sizes onto `SonicNocturneTypography` styles.
- [ ] Shape tokens: `small 8`, `medium 14`, `large 18`, `xl 26` dp; replace 17 radii.
- [ ] Icons: SkipPrevious/SkipNext everywhere; one animated "now playing" equalizer indicator (static when paused) used by SongRow, Queue, Album, Artist, Playlist.
- [ ] Titles: `displayTitle` everywhere a row/title is shown; Now Playing header shows "Now Playing" or album/context, not the title again.
- [ ] One `SongOptionsMenu` and one `AddToPlaylistSheet` shared by SongRow, Queue, Now Playing, Playlist; sentence-case copy throughout.
- [ ] Align reorder gesture (handle-drag) between Queue and PlaylistDetail.

---

## Phase 8 — Network, data cost & cleanup

**Files:** `MusicPlayerViewModel.kt`, `…/service/PreBufferManager.kt`, `…/data/local/DownloadRepository.kt`, `…/data/network/NetworkService.kt`

- [ ] Remove full-file `prefetchSong` for the next track; rely on ExoPlayer cache + `PreBufferManager` (skip pre-buffer on metered unless user opts in).
- [ ] `PreBufferManager`: keyed jobs per song; only the true next song (`QueueOps.upcoming().first()`) is pre-buffered.
- [ ] Radio resolution: single shared fetch per seed song for both up-next and recommendations, `Semaphore(3)` around searches, cache by seed id. Guard `fetchUpNext` with an atomic in-flight flag set synchronously.
- [ ] `syncStateFromController`: merge into the existing `currentSong` (by id) instead of replacing it; restore queue from Phase 2 persistence.
- [ ] `BASE_URL` from `BuildConfig` (debug emulator vs LAN/prod).
- [ ] Stream URL cache: add TTL for YouTube URLs.
- [ ] Delete unused: `EqualizerBottomSheet` (or wire it into Now Playing menu — decide), `PulsingArtworkHalo`, `GlassmorphismCard`, `toggleQueue`/`isQueueVisible`, `updateDominantColor`, `downloadPlaylist`, `loadUserName`/`loadRecentSearches`/`loadLastSession` stubs.
- [ ] Reduce `catch (_: Exception) {}` (≈50 sites) to logged handling; rethrow `CancellationException`.

---

## Phase summary

| Phase | Theme | Findings | Size |
|---|---|---|---|
| 0 | Queue logic extraction + tests | foundation | M |
| 1 | Critical playback correctness | #1-4, #6, #7 | M |
| 2 | Queue ownership in service | #5 | L |
| 3 | Next / previous semantics | #8, #11-14 | M |
| 4 | Queue screen | #15-21 | M |
| 5 | Animation micro-polish | #9, #10, #22-29 | M |
| 6 | Interaction bugs | #30-37 | M |
| 7 | Design-system consistency | consistency list | L |
| 8 | Network, data cost, cleanup | data/network list | M |

**Open decisions**
- Playlist end with repeat OFF: continue into radio, or stop? (Phase 3)
- Equalizer: wire into Now Playing or delete? (Phase 8)
- Phase 2 can be deferred if background-close playback is not a priority; Phases 3-8 do not depend on it.
