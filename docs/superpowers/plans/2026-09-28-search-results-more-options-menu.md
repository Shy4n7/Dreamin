# Search Results Three-Dots Menu ("More Options") Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a modern three-dots (`MoreVert`) overflow options button next to the queue icon on search result song rows, adopting the high-fidelity styling established in `PlaylistDetailScreen` (16dp rounded card, 1dp outline border, fixed 210dp width, semantic typography tokens) and providing "Add to playlist", "Add to queue", "Go to album", and "Download song".

**Architecture:**
1. **Glassmorphic Dropdown Styling (Standardized from `PlaylistDetailScreen`):**
   - Container: `colors.surfaceHighest` with `RoundedCornerShape(16.dp)`
   - Border: `1.dp, colors.outlineVariant`
   - Fixed width: `210.dp`
   - Typography: 14sp, `FontWeight.Medium`, `colors.onSurface`
   - Leading Icons: 20dp, tinted with semantic tokens (`colors.onSurface`, `colors.primary`, or `colors.error`)
2. **Action Handlers:**
   - **Add to playlist:** Sets `showPlaylistPicker = true`, presenting `AddToPlaylistDialog` for playlist selection/creation.
   - **Add to queue:** Invokes `onAddToQueue(song)`, appending to playback queue with tactile haptic feedback.
   - **Go to album:** Calls `vm.openAlbumForSong(song)`, checking existing search album results first and falling back to querying JioSaavn/YouTube by album title to display `AlbumDetailScreen`.
   - **Download song:** Toggles download state via `vm.downloadSong(song)` or `vm.deleteDownload(song.id)` with active download progress indicator.
3. **Modular Component Flow:**
   - [`CommonComponents.kt`](file:///c:/Users/shyan/Desktop/Projects/Dreamin/app/src/main/java/com/shyan/dreamin/ui/screens/CommonComponents.kt): Extend `SongRow` with more-options overflow trigger, dropdown menu, and playlist dialog state.
   - [`HomeScreen.kt`](file:///c:/Users/shyan/Desktop/Projects/Dreamin/app/src/main/java/com/shyan/dreamin/ui/screens/HomeScreen.kt): Plumb `onGoToAlbum`, `onDownloadSong`, `downloadedSongIds`, and `downloadingSongIds` into `SearchResults`.
   - [`MusicPlayerScreen.kt`](file:///c:/Users/shyan/Desktop/Projects/Dreamin/app/src/main/java/com/shyan/dreamin/ui/screens/MusicPlayerScreen.kt): Connect scaffold handlers to `vm`.
   - [`MusicPlayerViewModel.kt`](file:///c:/Users/shyan/Desktop/Projects/Dreamin/app/src/main/java/com/shyan/dreamin/viewmodel/MusicPlayerViewModel.kt): Add `openAlbumForSong(song: Song)`.

**Tech Stack:** Jetpack Compose Material 3, Kotlin Coroutines, ViewModel StateFlow, `LocalDreaminColors` design tokens.

**Spec:** User requests:
- "add the three dots button on the right near queue whichas add to playlist and plan on other feature could be added. dont implement it. thhis is serch screen, not the queue screen and the resylts of serch should have the three dots"
- "aadd 'add to playlist, add to queue, go to album, download song'. plan for it"
- "find the other dropdownboxes and say me the ui differences"
- "now combine this recommendation and previous implementation plan and give me a plan"

## Global Constraints
- Strictly adhere to `LocalDreaminColors.current` design tokens; no hardcoded colors (`Color.White` or hex codes).
- Touch targets must be >= 44x44dp.
- Phase-deferred progress reads and lifecycle-aware state flow collection (`collectAsStateWithLifecycle()`).
- All changes must pass `.\gradlew.bat compileDebugKotlin testDebugUnitTest`.

---

### Task 1: Add `openAlbumForSong` to `MusicPlayerViewModel`

**Files:**
- Modify: `app/src/main/java/com/shyan/dreamin/viewmodel/MusicPlayerViewModel.kt:2170-2240`
- Test: `app/src/test/java/com/shyan/dreamin/viewmodel/AlbumNavigationTest.kt`

**Interfaces:**
- Consumes: `song: Song`, `state.searchAlbumResults`, `searchAlbumsOnDevice()`, `openAlbum(album: AlbumItem)`
- Produces: `fun openAlbumForSong(song: Song)`

- [ ] **Step 1: Write unit test for `openAlbumForSong` resolution logic**
  - Create test verifying that if `searchAlbumResults` contains an album matching `song.album` or movie title parsed from `song.title`, it selects that album immediately.
  - Verify that when `song.album` is blank, it parses from `(From "Movie")` or falls back to song title.

- [ ] **Step 2: Implement `openAlbumForSong` in `MusicPlayerViewModel.kt`**
  ```kotlin
  fun openAlbumForSong(song: Song) {
      val movieFromTitle = song.title.substringAfter("(From \"", "").substringBefore("\")").trim()
      val targetName = when {
          song.album.isNotBlank() -> song.album.trim()
          movieFromTitle.isNotBlank() -> movieFromTitle
          else -> song.title.trim()
      }

      val inMemory = _uiState.value.searchAlbumResults.firstOrNull {
          it.title.equals(targetName, ignoreCase = true) ||
          it.title.contains(targetName, ignoreCase = true) ||
          targetName.contains(it.title, ignoreCase = true)
      }

      if (inMemory != null) {
          openAlbum(inMemory)
          return
      }

      viewModelScope.launch(Dispatchers.IO) {
          val searchResults = searchAlbumsOnDevice(targetName, limit = 4)
          val bestMatch = searchResults.firstOrNull {
              it.title.contains(targetName, ignoreCase = true) ||
              targetName.contains(it.title, ignoreCase = true)
          } ?: searchResults.firstOrNull()

          if (bestMatch != null) {
              withContext(Dispatchers.Main) {
                  openAlbum(bestMatch)
              }
          }
      }
  }
  ```

- [ ] **Step 3: Run unit tests**
  - Run `.\gradlew.bat testDebugUnitTest` to verify correctness.

---

### Task 2: Standardize Dropdown UI & Add Three-Dots Menu to `SongRow` in `CommonComponents.kt`

**Files:**
- Modify: `app/src/main/java/com/shyan/dreamin/ui/screens/CommonComponents.kt:630-905`

**Interfaces:**
- Consumes:
  - `song: Song`
  - `isPlaying: Boolean`
  - `onClick: () -> Unit`
  - `onAddToQueue: () -> Unit`
  - `onPlayNext: () -> Unit = {}`
  - `rank: Int? = null`
  - `onAddToPlaylist: (Long) -> Unit = {}`
  - `onGoToAlbum: (() -> Unit)? = null`
  - `onDownload: (() -> Unit)? = null`
  - `isDownloaded: Boolean = false`
  - `isDownloading: Boolean = false`
  - `showMoreOptions: Boolean = true`
- Produces: Trailing action cluster with Queue button and 3-dots Overflow Menu formatted to the 16dp rounded `PlaylistDetailScreen` design pattern.

- [ ] **Step 1: Add new parameters to `SongRow` with backward-compatible defaults**
  - Add `onGoToAlbum: (() -> Unit)? = null`
  - Add `onDownload: (() -> Unit)? = null`
  - Add `isDownloaded: Boolean = false`
  - Add `isDownloading: Boolean = false`
  - Add `showMoreOptions: Boolean = true`

- [ ] **Step 2: Implement the standardized Dropdown UI beside Queue button**
  ```kotlin
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp)
  ) {
      // 1. Add to Queue Quick Button
      IconButton(onClick = onAddToQueue, modifier = Modifier.size(36.dp)) {
          Icon(
              Icons.AutoMirrored.Outlined.PlaylistAdd,
              contentDescription = "Add to queue",
              tint = colors.onSurfaceVariant,
              modifier = Modifier.size(20.dp)
          )
      }

      // 2. Three-Dots More Options
      if (showMoreOptions) {
          var showOverflowMenu by remember { mutableStateOf(false) }

          Box {
              IconButton(
                  onClick = { showOverflowMenu = true },
                  modifier = Modifier.size(36.dp)
              ) {
                  Icon(
                      imageVector = Icons.Filled.MoreVert,
                      contentDescription = "More options",
                      tint = colors.onSurfaceVariant,
                      modifier = Modifier.size(20.dp)
                  )
              }

              DropdownMenu(
                  expanded = showOverflowMenu,
                  onDismissRequest = { showOverflowMenu = false },
                  modifier = Modifier
                      .background(colors.surfaceHighest, RoundedCornerShape(16.dp))
                      .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                      .width(210.dp)
              ) {
                  // Item 1: Add to playlist
                  DropdownMenuItem(
                      text = {
                          Text(
                              "Add to playlist",
                              color = colors.onSurface,
                              fontSize = 14.sp,
                              fontWeight = FontWeight.Medium
                          )
                      },
                      leadingIcon = {
                          Icon(
                              Icons.AutoMirrored.Filled.PlaylistAdd,
                              contentDescription = null,
                              tint = colors.onSurface,
                              modifier = Modifier.size(20.dp)
                          )
                      },
                      onClick = {
                          showOverflowMenu = false
                          showPlaylistPicker = true
                      }
                  )

                  // Item 2: Add to queue
                  DropdownMenuItem(
                      text = {
                          Text(
                              "Add to queue",
                              color = colors.onSurface,
                              fontSize = 14.sp,
                              fontWeight = FontWeight.Medium
                          )
                      },
                      leadingIcon = {
                          Icon(
                              Icons.AutoMirrored.Outlined.QueueMusic,
                              contentDescription = null,
                              tint = colors.onSurface,
                              modifier = Modifier.size(20.dp)
                          )
                      },
                      onClick = {
                          showOverflowMenu = false
                          onAddToQueue()
                      }
                  )

                  // Item 3: Go to album
                  if (onGoToAlbum != null) {
                      DropdownMenuItem(
                          text = {
                              Text(
                                  "Go to album",
                                  color = colors.onSurface,
                                  fontSize = 14.sp,
                                  fontWeight = FontWeight.Medium
                              )
                          },
                          leadingIcon = {
                              Icon(
                                  Icons.Filled.Album,
                                  contentDescription = null,
                                  tint = colors.onSurface,
                                  modifier = Modifier.size(20.dp)
                              )
                          },
                          onClick = {
                              showOverflowMenu = false
                              onGoToAlbum()
                          }
                      )
                  }

                  // Item 4: Download song
                  if (onDownload != null) {
                      DropdownMenuItem(
                          text = {
                              Text(
                                  if (isDownloaded) "Delete download" else if (isDownloading) "Downloading..." else "Download",
                                  color = if (isDownloaded) colors.error else colors.onSurface,
                                  fontSize = 14.sp,
                                  fontWeight = FontWeight.Medium
                              )
                          },
                          leadingIcon = {
                              if (isDownloading) {
                                  CircularProgressIndicator(
                                      modifier = Modifier.size(18.dp),
                                      color = colors.secondary,
                                      strokeWidth = 2.dp
                                  )
                              } else {
                                  Icon(
                                      imageVector = if (isDownloaded) Icons.Outlined.Delete else Icons.Outlined.Download,
                                      contentDescription = null,
                                      tint = if (isDownloaded) colors.error else colors.onSurface,
                                      modifier = Modifier.size(20.dp)
                                  )
                              }
                          },
                          onClick = {
                              showOverflowMenu = false
                              onDownload()
                          }
                      )
                  }
              }
          }
      }
  }
  ```

- [ ] **Step 3: Verify compilation**
  - Run `.\gradlew.bat compileDebugKotlin` to ensure no lint/type errors.

---

### Task 3: Plumb Search Screen in `HomeScreen.kt` & `MusicPlayerScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/shyan/dreamin/ui/screens/HomeScreen.kt:768-1005`
- Modify: `app/src/main/java/com/shyan/dreamin/ui/screens/MusicPlayerScreen.kt:280-320`

**Interfaces:**
- Consumes: `vm.openAlbumForSong`, `vm.downloadSong`, `state.downloadedSongs`, `state.downloadingSongIds`
- Produces: Seamless binding between ViewModel state and search row overflow actions.

- [ ] **Step 1: Add download and album callbacks to `SearchResults` in `HomeScreen.kt`**
  ```kotlin
  fun SearchResults(
      ...
      onGoToAlbum: (Song) -> Unit = {},
      onDownloadSong: (Song) -> Unit = {},
      downloadedSongIds: Set<String> = emptySet(),
      downloadingSongIds: Set<String> = emptySet()
  )
  ```

- [ ] **Step 2: Bind parameters to `SongRow` in `SearchResults`**
  ```kotlin
  SongRow(
      song = song,
      isPlaying = currentSong?.id == song.id,
      onClick = { onSongClick(song) },
      onAddToQueue = { onAddToQueue(song) },
      onAddToPlaylist = { playlistId -> onAddToPlaylist(song, playlistId) },
      onGoToAlbum = { onGoToAlbum(song) },
      onDownload = { onDownloadSong(song) },
      isDownloaded = downloadedSongIds.contains(song.id),
      isDownloading = downloadingSongIds.contains(song.id)
  )
  ```

- [ ] **Step 3: Connect handlers in `MusicPlayerScreen.kt`**
  - Forward `onGoToAlbum = vm::openAlbumForSong`
  - Forward `onDownloadSong = vm::downloadSong`
  - Forward `downloadedSongIds = remember(state.downloadedSongs) { state.downloadedSongs.map { it.id }.toSet() }`
  - Forward `downloadingSongIds = state.downloadingSongIds`

---

### Task 4: Verification, Quality Gates & Local Git Commit

**Files:**
- Verification on connected emulator `emulator-5554`

- [ ] **Step 1: Execute test suite and compile gate**
  - Run `.\gradlew.bat compileDebugKotlin testDebugUnitTest`
  - Confirm 0 errors and all unit tests pass.

- [ ] **Step 2: Install APK on device**
  - Run `.\gradlew.bat installDebug`
  - Launch app: `adb -s emulator-5554 shell am start -n com.shyan.dreamin/.MainActivity`

- [ ] **Step 3: Visual and behavioral verification**
  - Navigate to Search, query a song (e.g., "magale").
  - Verify that each row displays the `+ Queue` icon and the `MoreVert` three-dots icon side by side.
  - Tap `MoreVert`: verify the dropdown menu opens with 16dp rounded corners, 1dp outline border, 210dp width.
  - Test all 4 actions:
    1. "Add to playlist": Dialog appears with playlist list and "Create new playlist".
    2. "Add to queue": Song added to upcoming tracks, haptic feedback fires.
    3. "Go to album": Opens `AlbumDetailScreen` with album songs.
    4. "Download": Download begins, spinner or check indicator displays.
  - Capture verification screenshot.

- [ ] **Step 4: Automatic Local Git Commit**
  - Run `git add .`
  - Run `git commit -m "feat(search): add three-dots overflow menu to search result songs with standardized dropdown styling"`
  - Never run `git push`.
