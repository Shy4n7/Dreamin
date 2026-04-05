# Dreamin

A personal Android music streaming app with a self-hosted FastAPI backend. Search, stream, and discover music — with smart queue generation, personalized recommendations, and lock screen controls.

## Screenshots

> Coming soon

## Features

- **Search & Stream** — Search millions of songs and stream at 320kbps
- **Smart Queue** — Radio-style queue built from genre, artist, and language signals
- **Personalized Recommendations** — Suggestions based on your listening history
- **Trending Charts** — Language-aware charts (Tamil, Hindi, English, Telugu, Malayalam)
- **Lock Screen Controls** — Full playback controls on the lock screen and notification shade
- **Playlists** — Create and manage custom playlists
- **Favorites** — Save songs for quick access
- **Sleep Timer** — Schedule playback to stop automatically
- **Listening Stats** — Weekly stats, top songs, and top artists
- **Multiple Themes** — Sonic Nocturne, Blue Hour, Rose Dusk, Forest Night

## Tech Stack

### Android
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Playback**: Media3 / ExoPlayer with MediaSessionService
- **Networking**: Retrofit + OkHttp
- **Local Storage**: Room (database) + DataStore (preferences)
- **Image Loading**: Coil
- **Color Extraction**: Palette API
- **Min SDK**: 26 (Android 8.0)

### Backend
- **Framework**: FastAPI (Python 3.11)
- **Server**: Uvicorn
- **HTTP Client**: HTTPX (async)
- **Containerization**: Docker
- **Deployment**: ClawCloud (auto-deploy via GitHub Actions)

## Architecture

```
Android App
├── MusicPlayerViewModel     — single StateFlow, all business logic
├── MusicService             — foreground service, ExoPlayer + MediaSession
├── Room Database            — playlists, favorites, history, stats
├── DataStore                — user preferences, chart cache
└── Retrofit                 — communicates with FastAPI backend

FastAPI Backend
├── /api/mobile/search       — proxies JioSaavn search
├── /api/mobile/chart        — trending charts (cached 6h)
├── /api/mobile/play         — resolves stream URL, records play
├── /api/mobile/up_next      — generates smart queue candidates
└── /api/mobile/recommend    — personalized recommendations
```

## Getting Started

### Backend

**Run locally:**
```bash
cd backend
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8080 --reload
```

**Run with Docker:**
```bash
cd backend
docker build -t dreamin-server .
docker run -p 8080:8080 dreamin-server
```

### Android App

1. Open the project in Android Studio (Hedgehog or newer)
2. Set your server URL in `app/src/main/java/com/shyan/dreamin/data/network/NetworkService.kt`:
```kotlin
// Emulator
var BASE_URL = "http://10.0.2.2:8080/"

// Physical device (same Wi-Fi as your PC)
var BASE_URL = "http://192.168.x.x:8080/"

// Production
var BASE_URL = "https://your-server-url.com/"
```
3. Hit **Run** in Android Studio

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | Health check |
| GET | `/api/mobile/health` | App health check |
| POST | `/api/mobile/register` | Register device |
| GET | `/api/mobile/search?q=` | Search songs |
| GET | `/api/mobile/chart?language=` | Trending chart |
| GET | `/api/mobile/play?id=&artist=&title=` | Get stream URL |
| GET | `/api/mobile/up_next?song_id=` | Queue suggestions |
| GET | `/api/mobile/recommend?song_id=` | Recommendations |

## Deployment

The backend auto-deploys to [ClawCloud](https://clawcloud.com) on every push to `main` that changes files in `backend/`.

**Required GitHub secrets:**
| Secret | Description |
|--------|-------------|
| `DOCKERHUB_USERNAME` | Docker Hub username |
| `DOCKERHUB_TOKEN` | Docker Hub access token |
| `CLAWCLOUD_DEPLOY_HOOK` | ClawCloud redeploy webhook URL |

## Project Structure

```
Dreamin/
├── app/                        # Android app
│   └── src/main/java/com/shyan/dreamin/
│       ├── data/
│       │   ├── local/          # Room database, DAOs, DataStore
│       │   ├── model/          # Data models
│       │   └── network/        # Retrofit API interface
│       ├── service/            # MusicService (ExoPlayer + MediaSession)
│       ├── viewmodel/          # MusicPlayerViewModel
│       └── ui/
│           ├── screens/        # Compose screens
│           └── theme/          # App themes and colors
├── backend/                    # FastAPI server
│   ├── app.py
│   ├── requirements.txt
│   └── Dockerfile
└── .github/workflows/          # CI/CD
    └── deploy-backend.yml
```

## License

Personal project — not licensed for redistribution.
