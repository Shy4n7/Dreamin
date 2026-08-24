<img width="1774" height="887" alt="ChatGPT Image Aug 20, 2026, 02_39_33 PM" src="https://github.com/user-attachments/assets/864e837d-9656-4a94-9579-e8bf8d0aeccc" />



# Dreamin

Dreamin is a minimalist, ad-free music experience designed for private listening — built to make music feel uninterrupted, personal, and alive.

No algorithms pushing sponsored content. No skips limits. Just music, the way you want it.

## Screenshots

> Coming soon

## Why I Built This

Every major streaming app makes the same tradeoffs — ads between songs, discovery driven by what pays, a UI designed to keep you scrolling rather than listening. I wanted something different: an app that gets out of the way and just plays music.

Dreamin started as something I built for myself and a few friends. It's lean, fast, and doesn't care about engagement metrics.

## Features

- **Search & Stream** — Find and play songs instantly, streamed at 320kbps
- **Smart Queue** — Radio-style queue built from genre, artist, and language signals — no manual curation needed
- **Personalized Recommendations** — Suggestions that actually reflect what you've been listening to
- **Trending Charts** — Language-aware charts: Tamil, Hindi, English, Telugu, Malayalam
- **Lock Screen Controls** — Full playback controls on the lock screen and notification shade
- **Playlists & Favorites** — Organize music your way
- **Sleep Timer** — Schedule playback to stop automatically
- **Listening Stats** — Weekly stats, top songs, top artists
- **Multiple Themes** — Sonic Nocturne, Blue Hour, Rose Dusk, Forest Night

## Tech Stack

### Android
| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Playback | Media3 / ExoPlayer + MediaSessionService |
| Networking | Retrofit + OkHttp |
| Local Storage | Room (database) + DataStore (preferences) |
| Image Loading | Coil + Palette API |
| Min SDK | 26 (Android 8.0) |

### Backend (Optional Admin Dashboard)
| Layer | Technology |
|-------|-----------|
| Framework | FastAPI (Python 3.11) |
| Server | Uvicorn (ASGI) |
| HTTP Client | HTTPX (async) |

## Architecture

```
Android App (Fully Standalone & Direct-Streaming)
├── MusicPlayerViewModel     — single StateFlow, all business logic & on-device fallbacks
├── MusicService             — foreground service, ExoPlayer + MediaSession
├── Room Database            — playlists, favorites, history, stats
├── DataStore                — user preferences, chart cache
└── On-Device Engine         — direct 320kbps stream resolution, search & queueing
```

---

## Getting Started

### Android App

1. Connect your Android device or start an emulator.
2. Run `run_app.bat` or open the project in Android Studio and hit **Run**.

### Backend (Optional)

If you wish to run the optional local admin analytics dashboard:
```bash
cd backend
pip install -r requirements.txt
python app.py
```

## API Reference (Optional Backend)

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
| GET | `/admin/users?token=` | Admin monitoring dashboard |

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
└── backend/                    # Optional FastAPI admin & analytics server
    ├── app.py
    └── requirements.txt
```

## What's Next

- Offline downloads — save songs for when you're off the grid
- Social listening — share what you're playing with friends
- Smarter recommendations — move beyond history-based signals toward taste modeling
- iOS support — same experience, different platform

## License

Personal project — not licensed for redistribution.
