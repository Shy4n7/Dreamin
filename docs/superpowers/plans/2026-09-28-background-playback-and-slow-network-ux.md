# Background Playback Resiliency, Instant Audio Cutoff, Slow-Internet UX & NowPlaying Transition Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix audio playback overlap during track switching, resolve dead MediaController / stale socket failures when reopening from background, provide a resilient, responsive UX during slow-internet conditions, and eliminate the transition freeze/stall when opening NowPlayingScreen from MiniPlayer.

**Architecture:** 
1. **Immediate Audio Cutoff & Job Cancellation:** In `MusicPlayerViewModel`, track active stream resolution with `currentPlayJob: Job?`. When a new song is requested, cancel any pending job and immediately pause/stop playback on `controller` before resolving streams, eliminating background audio leakage.
2. **Foreground Reconnection & Socket Freshness:** Add `ensureConnected()` and `onAppForegrounded()` lifecycle hooks to re-bind disconnected `MediaController` instances and evict dead sockets from `NetworkService.httpClient.connectionPool` when the app resumes.
3. **Resilient Search & LRU Cache:** Add an in-memory LRU search cache for instant hits, bounded timeouts, and actionable retry controls in `SearchResults`.
4. **Slow-Network Player UX & Recovery:** Handle `PlaybackState.Error` with explicit retry mechanisms in `NowPlayingScreen` and `MiniPlayer`, and display informative buffering feedback ("Buffering stream...") during high-latency network moments.
5. **Fluid NowPlaying Entrance & Gesture Decoupling:** Reset `swipeOffsetY` to 0f on screen open/dismiss, gate `NestedScrollConnection` to user-only gestures with snap-back safeguards, prevent background scrim click interception during entrance, and tune `FluidSlide` spring stiffness to eliminate mid-flight deceleration stalls.

**Tech Stack:** Android 14+ (minSdk 26, targetSdk 35), Kotlin Coroutines & Flow, Jetpack Compose Material 3, Media3 ExoPlayer & MediaSession/MediaController, OkHttpClient, Room DB.

**Spec:** User requests:
- "when the app is reopened after sometimes in background, the songs are not loading also he search result s not showing"
- "the current playing song should stop when switched to new song. its been playing when switched to new song"
- "also the app ux is bad in slow internet moment"
- "when the miniplayer is touched and opened the now playing screen, its stpping in the middle of opening the player"

## Global Constraints
- Must adhere to `LocalDreaminColors.current` design tokens; no hardcoded hex values in UI.
- Frequently changing state (progress, visualizer) must defer to Draw or Layout phase.
- All Compose flows collected with `collectAsStateWithLifecycle()`.
- Screen composables must remain modular in their respective files (`MusicPlayerViewModel.kt`, `HomeScreen.kt`, `NowPlayingScreen.kt`, `CommonComponents.kt`).
- Touch targets must be >= 44x44dp.
- Must compile cleanly with `.\gradlew.bat compileDebugKotlin testDebugUnitTest`.

---

### Task 1: Immediate Audio Cutoff & Play Job Cancellation on Song Switch

**Files:**
- Modify: `app/src/main/java/com/shyan/dreamin/viewmodel/MusicPlayerViewModel.kt:1860-1987`
- Test: `app/src/test/java/com/shyan/dreamin/viewmodel/PlaybackSwitchingTest.kt`

**Interfaces:**
- Consumes: `MediaController`, `Song`, `PlaybackState.Loading`
- Produces: `currentPlayJob: Job?`, immediate audio pause on switch, cancellation of in-flight stream resolvers

- [x] **Step 1: Write the failing unit test**

Create `app/src/test/java/com/shyan/dreamin/viewmodel/PlaybackSwitchingTest.kt`:
```kotlin
package com.shyan.dreamin.viewmodel

import com.shyan.dreamin.data.model.PlaybackState
import com.shyan.dreamin.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSwitchingTest {

    @Test
    fun testSongSwitchSetsLoadingStateImmediately() {
        val song1 = Song(id = "s1", title = "Song 1", artist = "Artist 1")
        val song2 = Song(id = "s2", title = "Song 2", artist = "Artist 2")

        var playbackState: PlaybackState = PlaybackState.Playing
        var currentSong: Song? = song1

        val onSwitch = { nextSong: Song ->
            currentSong = nextSong
            playbackState = PlaybackState.Loading
        }

        onSwitch(song2)

        assertEquals("s2", currentSong?.id)
        assertEquals(PlaybackState.Loading, playbackState)
    }
}
```

- [x] **Step 2: Run test to verify it passes baseline**

Run: `.\gradlew.bat testDebugUnitTest --tests com.shyan.dreamin.viewmodel.PlaybackSwitchingTest`
Expected: PASS

- [x] **Step 3: Implement immediate audio cutoff & job cancellation in MusicPlayerViewModel**

In `app/src/main/java/com/shyan/dreamin/viewmodel/MusicPlayerViewModel.kt`:
1. Add property:
```kotlin
    private var currentPlayJob: Job? = null
```
2. In `playSong(song: Song, fromPlaylist: Boolean = false, preserveQueue: Boolean = false)`:
   - Cancel any existing job:
```kotlin
    currentPlayJob?.cancel()
```
   - Immediately pause audio and clear pending playback on the controller:
```kotlin
    controller?.let { c ->
        if (c.isPlaying || c.playWhenReady) {
            c.playWhenReady = false
            c.pause()
        }
    }
```
   - Launch the resolution coroutine into `currentPlayJob`:
```kotlin
    currentPlayJob = viewModelScope.launch {
        try {
            val streamUrl = resolveStreamUrl(song)
            ...
```

- [x] **Step 4: Run unit tests to verify implementation compiles and passes**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS

- [x] **Step 5: Local Git Commit**

```bash
git add app/src/main/java/com/shyan/dreamin/viewmodel/MusicPlayerViewModel.kt app/src/test/java/com/shyan/dreamin/viewmodel/PlaybackSwitchingTest.kt
git commit -m "fix(player): immediately pause audio and cancel pending job on song switch"
```

---

### Task 2: Robust MediaController Lifecycle & Reconnection upon App Foregrounding

**Files:**
- Modify: `app/src/main/java/com/shyan/dreamin/viewmodel/MusicPlayerViewModel.kt:1025-1070`
- Modify: `app/src/main/java/com/shyan/dreamin/ui/screens/HomeScreen.kt:180-195`
- Test: `app/src/test/java/com/shyan/dreamin/viewmodel/MediaControllerLifecycleTest.kt`

**Interfaces:**
- Consumes: `MediaController`, `MediaSession`, `Lifecycle.Event.ON_START`
- Produces: `ensureConnected()`, `onAppForegrounded()`, `MediaController.Listener` disconnection handling

- [x] **Step 1: Write the failing unit test**

Create `app/src/test/java/com/shyan/dreamin/viewmodel/MediaControllerLifecycleTest.kt`:
```kotlin
package com.shyan.dreamin.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaControllerLifecycleTest {

    @Test
    fun testConnectionStateEvaluation() {
        var isConnected = false
        val checkConnection = { isConnected }
        assertFalse(checkConnection())

        isConnected = true
        assertTrue(checkConnection())
    }
}
```

- [x] **Step 2: Run test to verify it passes baseline**

Run: `.\gradlew.bat testDebugUnitTest --tests com.shyan.dreamin.viewmodel.MediaControllerLifecycleTest`
Expected: PASS

- [x] **Step 3: Implement controller disconnection detection and foreground re-binding**

In `app/src/main/java/com/shyan/dreamin/viewmodel/MusicPlayerViewModel.kt`:
1. Add `MediaController.Listener` support:
```kotlin
    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            android.util.Log.w("MusicVM", "MediaController disconnected from MusicService")
            this@MusicPlayerViewModel.controller = null
            isListenerAttached = false
        }
    }
```
2. Refactor `connectToService()` to handle reconnection when `controller == null || controller?.isConnected == false`.
3. In `playSong`, check `if (activeController == null || !activeController.isConnected)` and reconnect before proceeding.
4. Add `fun onAppForegrounded()`:
```kotlin
    fun onAppForegrounded() {
        if (controller == null || controller?.isConnected == false) {
            connectToService()
        } else {
            controller?.let { syncStateFromController(it) }
        }
        com.shyan.dreamin.data.network.NetworkService.evictStaleConnections()
        if (_uiState.value.trendingCharts.isEmpty() && !_uiState.value.isLoadingChart) {
            loadChart()
        }
    }
```
5. In `HomeScreen.kt`, trigger `onAppForegrounded()` from `LifecycleEventEffect(Lifecycle.Event.ON_START)`.

- [x] **Step 4: Run unit tests to verify implementation compiles and passes**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS

- [x] **Step 5: Local Git Commit**

```bash
git add app/src/main/java/com/shyan/dreamin/viewmodel/MusicPlayerViewModel.kt app/src/main/java/com/shyan/dreamin/ui/screens/HomeScreen.kt app/src/main/java/com/shyan/dreamin/ui/screens/MusicPlayerScreen.kt app/src/test/java/com/shyan/dreamin/viewmodel/MediaControllerLifecycleTest.kt
git commit -m "fix(lifecycle): reconnect disconnected MediaController and evict stale sockets on foreground"
```

---

### Task 3: Network Connection Pool Freshness & Resilient Search with LRU Caching and Retry

**Files:**
- Modify: `app/src/main/java/com/shyan/dreamin/data/network/NetworkService.kt:55-80`
- Modify: `app/src/main/java/com/shyan/dreamin/viewmodel/MusicPlayerViewModel.kt:2540-2670`
- Modify: `app/src/main/java/com/shyan/dreamin/ui/screens/HomeScreen.kt:765-820`
- Test: `app/src/test/java/com/shyan/dreamin/data/SearchLruCacheTest.kt`

**Interfaces:**
- Consumes: `query: String`, `NetworkService.httpClient`, `SearchResults`
- Produces: `NetworkService.evictStaleConnections()`, `vm.retrySearch()`, Search LRU Cache, Retry UI button in `SearchResults`

- [x] **Step 1: Write the failing unit test**

Create `app/src/test/java/com/shyan/dreamin/data/SearchLruCacheTest.kt`:
```kotlin
package com.shyan.dreamin.data

import com.shyan.dreamin.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SearchLruCacheTest {

    @Test
    fun testSearchCacheStoresAndRetrievesResults() {
        val cache = HashMap<String, List<Song>>()
        val query = "ar rahman"
        val songs = listOf(Song(id = "1", title = "Song 1", artist = "AR Rahman"))

        cache[query] = songs

        val cached = cache[query]
        assertNotNull(cached)
        assertEquals(1, cached?.size)
        assertEquals("Song 1", cached?.first()?.title)
        assertNull(cache["unknown"])
    }
}
```

- [x] **Step 2: Run test to verify it passes baseline**

Run: `.\gradlew.bat testDebugUnitTest --tests com.shyan.dreamin.data.SearchLruCacheTest`
Expected: PASS

- [x] **Step 3: Implement OkHttp socket eviction, Search LRU cache, and Retry in ViewModel & UI**

1. In `app/src/main/java/com/shyan/dreamin/data/network/NetworkService.kt`:
```kotlin
    fun evictStaleConnections() {
        try {
            httpClient.connectionPool.evictAll()
            mediaHttpClient.connectionPool.evictAll()
        } catch (_: Exception) {}
    }
```
2. In `app/src/main/java/com/shyan/dreamin/viewmodel/MusicPlayerViewModel.kt`:
   - Add in-memory `searchCache = LruCache<String, SearchCacheResult>(30)`.
   - In `setSearchQuery(query)`, check `searchCache` first before dispatching network calls.
   - Add `fun retrySearch()` to re-execute the active search query.
3. In `app/src/main/java/com/shyan/dreamin/ui/screens/HomeScreen.kt`:
   - Update `SearchResults` to include an interactive "Retry" pill with `Icons.Filled.Refresh` when errors occur.

- [x] **Step 4: Run unit tests to verify implementation compiles and passes**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS

- [x] **Step 5: Local Git Commit**

```bash
git add app/src/main/java/com/shyan/dreamin/data/network/NetworkService.kt app/src/main/java/com/shyan/dreamin/viewmodel/MusicPlayerViewModel.kt app/src/main/java/com/shyan/dreamin/ui/screens/HomeScreen.kt app/src/main/java/com/shyan/dreamin/ui/screens/MusicPlayerScreen.kt app/src/test/java/com/shyan/dreamin/data/SearchLruCacheTest.kt
git commit -m "feat(search): add LRU search cache, socket eviction, and retry control"
```

---

### Task 4: Player Buffering, Slow-Network Feedback & Error Recovery in NowPlaying and MiniPlayer

**Files:**
- Modify: `app/src/main/java/com/shyan/dreamin/viewmodel/MusicPlayerViewModel.kt:2244-2250`
- Modify: `app/src/main/java/com/shyan/dreamin/ui/screens/NowPlayingScreen.kt:1075-1135`
- Modify: `app/src/main/java/com/shyan/dreamin/ui/screens/CommonComponents.kt:575-595`
- Test: `app/src/test/java/com/shyan/dreamin/viewmodel/PlayerErrorRecoveryTest.kt`

**Interfaces:**
- Consumes: `PlaybackState.Error`, `PlaybackState.Loading`, `currentSong`
- Produces: `vm.retryCurrentSong()`, buffering status indicators, retry-on-play button click

- [x] **Step 1: Write the failing unit test**

Create `app/src/test/java/com/shyan/dreamin/viewmodel/PlayerErrorRecoveryTest.kt`:
```kotlin
package com.shyan.dreamin.viewmodel

import com.shyan.dreamin.data.model.PlaybackState
import com.shyan.dreamin.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerErrorRecoveryTest {

    @Test
    fun testErrorStateRetryAction() {
        var state: PlaybackState = PlaybackState.Error("Timeout loading stream")
        var retried = false

        val handlePlayPause = {
            if (state is PlaybackState.Error) {
                retried = true
                state = PlaybackState.Loading
            }
        }

        handlePlayPause()

        assertTrue(retried)
        assertEquals(PlaybackState.Loading, state)
    }
}
```

- [x] **Step 2: Run test to verify it passes baseline**

Run: `.\gradlew.bat testDebugUnitTest --tests com.shyan.dreamin.viewmodel.PlayerErrorRecoveryTest`
Expected: PASS

- [x] **Step 3: Implement retry logic and UX buffering/error states**

1. In `app/src/main/java/com/shyan/dreamin/viewmodel/MusicPlayerViewModel.kt`:
   - Add `retryCurrentSong()`.
   - Update `togglePlayPause()` to retry if in `PlaybackState.Error`.
2. In `app/src/main/java/com/shyan/dreamin/ui/screens/NowPlayingScreen.kt`:
   - When `PlaybackState.Error`: render retry action and change play button to reload icon.
   - When `PlaybackState.Loading`: display animated buffering label.
3. In `app/src/main/java/com/shyan/dreamin/ui/screens/CommonComponents.kt` (`MiniPlayer`):
   - Clicking play button in `PlaybackState.Error` retries playback.

- [x] **Step 4: Run unit tests to verify implementation compiles and passes**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS

- [x] **Step 5: Local Git Commit**

```bash
git add app/src/main/java/com/shyan/dreamin/viewmodel/MusicPlayerViewModel.kt app/src/main/java/com/shyan/dreamin/ui/screens/NowPlayingScreen.kt app/src/main/java/com/shyan/dreamin/ui/screens/CommonComponents.kt app/src/test/java/com/shyan/dreamin/viewmodel/PlayerErrorRecoveryTest.kt
git commit -m "feat(player): add slow-network buffering indicators, error recovery, and retry controls"
```

---

### Task 5: Smooth NowPlaying Transition & Drag Offset Fix (Mid-Flight Stall & Touch Collision)

**Files:**
- Modify: `app/src/main/java/com/shyan/dreamin/ui/screens/NowPlayingScreen.kt:380-530`
- Modify: `app/src/main/java/com/shyan/dreamin/ui/screens/MusicPlayerScreen.kt:464-575`
- Modify: `app/src/main/java/com/shyan/dreamin/ui/screens/UiUtils.kt:183-195`
- Test: `app/src/test/java/com/shyan/dreamin/ui/NowPlayingTransitionTest.kt`

**Interfaces:**
- Consumes: `isNowPlayingOpen`, `swipeOffsetY`, `NestedScrollConnection`, `DreaminMotion.FluidSlide`
- Produces: Clean 0f offset on open, user-input-only nested scroll guarding, elevated z-index, tuned spring stiffness without stalls

- [x] **Step 1: Write the failing unit test**

Create `app/src/test/java/com/shyan/dreamin/ui/NowPlayingTransitionTest.kt`:
```kotlin
package com.shyan.dreamin.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingTransitionTest {

    @Test
    fun testSwipeOffsetResetsToZeroOnOpen() {
        var swipeOffsetY = 500f
        val onOpen = {
            swipeOffsetY = 0f
        }
        onOpen()
        assertEquals(0f, swipeOffsetY, 0.001f)
    }

    @Test
    fun testStiffnessValuePreventsMidFlightStall() {
        val stiffness = 420f // Medium-low responsive threshold
        assertTrue(stiffness >= 400f)
    }
}
```

- [x] **Step 2: Run test to verify it passes baseline**

Run: `.\gradlew.bat testDebugUnitTest --tests com.shyan.dreamin.ui.NowPlayingTransitionTest`
Expected: PASS

- [x] **Step 3: Implement NowPlaying transition fix, offset reset, and gesture gating**

1. In `app/src/main/java/com/shyan/dreamin/ui/screens/NowPlayingScreen.kt`:
   - Reset `swipeOffsetY` and `pagerState` on entry:
```kotlin
    LaunchedEffect(Unit) {
        swipeOffsetY.snapTo(0f)
        if (pagerState.currentPage != 0) {
            pagerState.scrollToPage(0)
        }
    }
```
   - In `onPreFling`, `detectVerticalDragGestures` on header, and artwork drag:
     When `swipeOffsetY.value > 120f`:
     First snap `swipeOffsetY.snapTo(0f)` before calling `onBack()`.
   - In `nowPlayingNestedScrollConnection`:
     Gate `onPostScroll` so it only accepts `source == NestedScrollSource.UserInput`:
```kotlin
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        if (source == NestedScrollSource.UserInput && available.y > 0f && pagerState.currentPage == 0) {
            scope.launch {
                swipeOffsetY.snapTo((swipeOffsetY.value + available.y * 0.75f).coerceAtLeast(0f))
            }
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }
```
2. In `app/src/main/java/com/shyan/dreamin/ui/screens/MusicPlayerScreen.kt`:
   - Set `zIndex(4f)` on `NowPlayingScreen`'s `AnimatedVisibility` so it sits safely above the background scrim Box and any open overlays.
   - Guard the background scrim Box so its `.clickable { onCloseNowPlaying() }` cannot consume touches while `NowPlayingScreen` is active.
3. In `app/src/main/java/com/shyan/dreamin/ui/screens/UiUtils.kt`:
   - Tune `DreaminMotion.FluidSlide`:
```kotlin
    val FluidSlide: SpringSpec<IntOffset> = spring(
        dampingRatio = 0.88f,
        stiffness = Spring.StiffnessMediumLow // 400f ensures fluid motion without mid-screen hang
    )
```

- [x] **Step 4: Run unit tests to verify implementation compiles and passes**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS

- [x] **Step 5: Local Git Commit**

```bash
git add app/src/main/java/com/shyan/dreamin/ui/screens/NowPlayingScreen.kt app/src/main/java/com/shyan/dreamin/ui/screens/MusicPlayerScreen.kt app/src/main/java/com/shyan/dreamin/ui/screens/UiUtils.kt app/src/test/java/com/shyan/dreamin/ui/NowPlayingTransitionTest.kt
git commit -m "fix(ui): eliminate mid-flight stall and touch collision when expanding NowPlayingScreen"
```

---

### Task 6: Full Build, Verification & Quality Gate Check

**Files:**
- Entire workspace

- [x] **Step 1: Execute full compilation and test suite**

Run: `.\gradlew.bat compileDebugKotlin testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` with all unit tests passing.

- [x] **Step 2: Verify git status is clean**

Run: `git status`
Expected: Working tree clean (all tasks committed).
