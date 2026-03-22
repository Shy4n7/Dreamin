# Dreamin Server

FastAPI backend for the Dreamin Android music app.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/mobile/health` | Health check |
| GET | `/api/mobile/search?q=` | Search songs |
| GET | `/api/mobile/chart` | Trending chart (cached 6h) |
| GET | `/api/mobile/play?id=&artist=&title=` | Get stream URL |
| GET | `/api/mobile/up_next?song_id=` | Queue suggestions |
| GET | `/api/mobile/recommend?song_id=` | Personalised recommendations |

## Run locally

```bash
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8080 --reload
```

## Run with Docker

```bash
docker build -t dreamin-server .
docker run -p 8080:8080 dreamin-server
```

## Connect Android app

In `NetworkService.kt`, set:
```kotlin
var BASE_URL = "http://<YOUR_PC_IP>:8080/"
```
