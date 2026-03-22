# 🎵 Resonance — Personal Android Music App

> Kotlin + Jetpack Compose frontend · Same Daydreamin FastAPI backend · ExoPlayer + MediaSession for lock screen controls

---

## Architecture

```
Android App (Kotlin/Compose)
    │
    ├── ExoPlayer (Media3)         ← audio playback
    ├── MediaSessionService        ← lock screen + notification controls (auto)
    ├── MediaController (ViewModel)← bridges UI ↔ Service
    └── Retrofit                   ← talks to your FastAPI server
            │
            ▼
    FastAPI Server (Daydreamin)    ← same server, zero changes
            ├── iTunes API         ← search + metadata
            ├── yt-dlp             ← YouTube audio stream
            └── songs.json         ← recommendations
```

---

## File Structure

```
app/src/main/
├── AndroidManifest.xml
└── java/com/shyan/resonance/
    ├── MainActivity.kt
    ├── data/
    │   ├── model/Song.kt              ← Song, API responses, UI state
    │   └── network/NetworkService.kt  ← Retrofit + API interface
    ├── service/
    │   └── MusicService.kt            ← Foreground service (ExoPlayer + MediaSession)
    ├── viewmodel/
    │   └── MusicPlayerViewModel.kt    ← All logic + state (StateFlow)
    └── ui/
        ├── theme/Theme.kt             ← Dark palette (DeepBlack + Cyan)
        └── screens/MusicPlayerScreen.kt ← Full Compose UI
```

---

## Setup

### 1. Kotlin / Android project
- Create a new Android project in Android Studio
- Package name: `com.shyan.resonance`
- Min SDK: 26 (Android 8.0)
- Language: Kotlin
- Build config: Kotlin DSL

### 2. Paste the build.gradle.kts
Replace your `app/build.gradle.kts` with the one provided.

### 3. Set your server IP
In `NetworkService.kt`, change the `BASE_URL`:
```kotlin
// Android Emulator:
var BASE_URL = "http://10.0.2.2:499/"

// Real phone on same WiFi as your PC:
var BASE_URL = "http://192.168.x.x:499/"   // ← your PC's IP
```

To find your PC's IP on Windows: `ipconfig`
To find on Mac/Linux: `ifconfig | grep "inet " | grep -v 127.0.0.1`

### 4. Start the FastAPI server (Daydreamin)
```bash
cd Server
source venv/bin/activate
python app.py
```

### 5. Build & Run
Hit ▶️ in Android Studio.

---

## Features

| Feature | Implementation |
|---|---|
| Search songs | `/api/mobile/search` → Retrofit |
| Trending charts on launch | `/api/mobile/chart` → Retrofit |
| Audio streaming | ExoPlayer streams the URL from `/api/mobile/play` |
| Background playback | `MusicService` (foreground service) |
| Lock screen controls | Media3 MediaSession (auto-generated) |
| Notification (play/pause/skip) | Media3 MediaSessionService (auto-generated) |
| Queue / Up Next | `/api/mobile/up_next` + local list |
| Recommendations | `/api/mobile/recommend` |
| Dynamic background | Album art blurred behind UI |
| Progress seek bar | `Slider` → `controller.seekTo()` |
| Headphone unplug pause | `setHandleAudioBecomingNoisy(true)` |
| Audio focus | Handled by ExoPlayer automatically |

---

## Key Design Decisions

### Why Media3 MediaSessionService?
On iOS, Daydreamin used `MPNowPlayingInfoCenter` + `MPRemoteCommandCenter` manually.
On Android, Media3's `MediaSessionService` does all of it automatically:
- Lock screen album art, title, artist
- Play/pause/skip buttons on lock screen
- Persistent notification with controls
- Hardware media button support

Zero manual notification code needed.

### Why StateFlow?
The ViewModel exposes a single `PlayerUiState` via `StateFlow`. The UI collects it with
`collectAsStateWithLifecycle()` for automatic lifecycle-aware updates.

### Why MediaController?
`MediaController` connects the ViewModel to `MusicService` over a session.
This means even if the Activity is destroyed (user swipes away), the Service keeps playing,
and re-connecting when the app re-opens gives instant state sync.

---

## Customization

### Change server URL at runtime
You can expose a settings screen and update `NetworkService.BASE_URL` from SharedPreferences.

### Add lyrics
Add to `MusicApi`:
```kotlin
@GET("api/mobile/lyrics")
suspend fun getLyrics(
    @Query("artist") artist: String,
    @Query("title") title: String
): LyricsResponse
```

### Dynamic album art color extraction
Use `androidx.palette:palette-ktx`. In your Composable:
```kotlin
LaunchedEffect(song.artworkUrl) {
    val bitmap = // load with Coil's ImageLoader as Bitmap
    val palette = Palette.from(bitmap).generate()
    vm.updateDominantColor(palette.getDarkVibrantColor(0xFF1A1A2E.toInt()))
}
```

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Can't reach server on real phone | Make sure phone + PC are on same WiFi. Use PC's LAN IP, not `localhost`. |
| Stream URL not working | Start FastAPI server first. Check `usesCleartextTraffic="true"` in Manifest. |
| No notification showing | Grant notification permission (Android 13+) — the app requests this on launch. |
| Audio cuts out in background | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission is in Manifest ✅ |
