# 🎵 Dreamin — Personal Android Music App

> Kotlin + Jetpack Compose frontend · FastAPI backend · ExoPlayer + MediaSession for lock screen controls

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

## Android App Setup

### 1. Kotlin / Android project
- Create a new Android project in Android Studio
- Package name: `com.shyan.resonance`
- Min SDK: 26 (Android 8.0)
- Language: Kotlin
- Build config: Kotlin DSL

### 2. Connect Android app
In `NetworkService.kt`, set your server IP:
```kotlin
// Android Emulator:
var BASE_URL = "http://10.0.2.2:8080/"

// Real phone on same WiFi as your PC:
var BASE_URL = "http://192.168.x.x:8080/"   // ← your PC's IP
```

### 3. Build & Run
Hit ▶️ in Android Studio.

---

## Server Setup

FastAPI backend for the Dreamin Android music app.

### Run locally

```bash
cd Server
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8080 --reload
```

### Run with Docker

```bash
cd Server
docker build -t dreamin-server .
docker run -p 8080:8080 dreamin-server
```

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/mobile/health` | Health check |
| GET | `/api/mobile/search?q=` | Search songs |
| GET | `/api/mobile/chart` | Trending chart (cached 6h) |
| GET | `/api/mobile/play?id=&artist=&title=` | Get stream URL |
| GET | `/api/mobile/up_next?song_id=` | Queue suggestions |
| GET | `/api/mobile/recommend?song_id=` | Personalised recommendations |

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

## Troubleshooting

| Problem | Fix |
|---|---|
| Can't reach server on real phone | Make sure phone + PC are on same WiFi. Use PC's LAN IP, not `localhost`. |
| Stream URL not working | Start FastAPI server first. Check `usesCleartextTraffic="true"` in Manifest. |
| No notification showing | Grant notification permission (Android 13+) — the app requests this on launch. |
| Audio cuts out in background | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission is in Manifest ✅ |
