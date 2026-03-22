"""
Dreamin — FastAPI backend server
Mirrors every endpoint the Android app calls via Retrofit.
"""

from __future__ import annotations

import asyncio
import hashlib
import json
import urllib.request
import urllib.parse
import os
import time
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

# ── Config ─────────────────────────────────────────────────────────────────────

CACHE_DIR = Path("song_cache")
DATA_DIR  = Path("data")
CACHE_DIR.mkdir(exist_ok=True)
DATA_DIR.mkdir(exist_ok=True)

PLAY_HISTORY_FILE = DATA_DIR / "play_history.json"
CHART_CACHE_FILE  = DATA_DIR / "chart_cache.json"

# ── Helpers ─────────────────────────────────────────────────────────────────────

def load_json(path: Path, default):
    if path.exists():
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    return default

def save_json(path: Path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

# ── Pydantic models (matches Android Song data class) ──────────────────────────

class Song(BaseModel):
    id: str
    title: str
    artist: str
    artwork_url: str = ""
    duration: int = 0  # ms

class SearchResponse(BaseModel):
    results: list[Song] = []

class ChartResponse(BaseModel):
    songs: list[Song] = []

class PlayResponse(BaseModel):
    stream_url: str
    proxy_url: Optional[str] = None

class UpNextResponse(BaseModel):
    songs: list[Song] = []

class RecommendResponse(BaseModel):
    recommendations: list[Song] = []

# ── iTunes search ──────────────────────────────────────────────────────────────

def itunes_search(query: str, limit: int = 15) -> list[Song]:
    """Search using iTunes API — fast, reliable, no API key needed."""
    params = urllib.parse.urlencode({
        "term": query,
        "limit": limit,
        "entity": "song"
    })
    url = f"https://itunes.apple.com/search?{params}"
    try:
        with urllib.request.urlopen(url, timeout=10) as r:
            data = json.loads(r.read())
        songs = []
        for item in data.get("results", []):
            songs.append(Song(
                id=str(item.get("trackId", "")),
                title=item.get("trackName", ""),
                artist=item.get("artistName", ""),
                artwork_url=item.get("artworkUrl100", "").replace(
                    "100x100", "500x500"
                ),
                duration=item.get("trackTimeMillis", 0),
            ))
        return songs
    except Exception as e:
        print(f"[itunes_search] error: {e}")
        return []

# ── Invidious streaming ────────────────────────────────────────────────────────

INVIDIOUS_INSTANCES = [
    "https://invidious.nerdvpn.de",
    "https://invidious.privacydev.net",
    "https://iv.melmac.space",
]

def get_stream_via_invidious(title: str, artist: str) -> str:
    """Search Invidious for the song and return the best audio stream URL."""
    query = urllib.parse.quote(f"{title} {artist}")

    for instance in INVIDIOUS_INSTANCES:
        try:
            # Search for the video
            search_url = f"{instance}/api/v1/search?q={query}&type=video"
            with urllib.request.urlopen(search_url, timeout=10) as r:
                results = json.loads(r.read())

            if not results:
                continue

            video_id = results[0].get("videoId")
            if not video_id:
                continue

            # Get streams for this video
            streams_url = f"{instance}/api/v1/videos/{video_id}"
            with urllib.request.urlopen(streams_url, timeout=10) as r:
                video_data = json.loads(r.read())

            # Get best audio stream
            audio_formats = video_data.get("adaptiveFormats", [])
            audio_only = [
                f for f in audio_formats
                if f.get("type", "").startswith("audio")
            ]

            if audio_only:
                # Sort by bitrate, pick best
                audio_only.sort(
                    key=lambda x: x.get("bitrate", 0),
                    reverse=True
                )
                return audio_only[0]["url"]

        except Exception as e:
            print(f"[invidious] {instance} failed: {e}")
            continue

    raise HTTPException(
        status_code=502,
        detail="All stream sources failed"
    )

# ── Play history (used for recommendations) ─────────────────────────────────────

def song_id(query: str) -> str:
    return hashlib.md5(query.encode()).hexdigest()[:16]

def record_play(song_id: str, title: str, artist: str):
    history: list = load_json(PLAY_HISTORY_FILE, [])
    history.append({"id": song_id, "title": title, "artist": artist, "ts": time.time()})
    # Keep last 200 entries
    history = history[-200:]
    save_json(PLAY_HISTORY_FILE, history)

def recent_artists(limit: int = 5) -> list[str]:
    history: list = load_json(PLAY_HISTORY_FILE, [])
    seen, artists = set(), []
    for entry in reversed(history):
        a = entry.get("artist", "")
        if a and a not in seen:
            seen.add(a)
            artists.append(a)
        if len(artists) >= limit:
            break
    return artists

# ── FastAPI app ─────────────────────────────────────────────────────────────────

app = FastAPI(title="Dreamin API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Endpoints ───────────────────────────────────────────────────────────────────

@app.get("/api/mobile/health")
async def health():
    return {"status": "ok", "server": "Dreamin"}


@app.get("/api/mobile/search", response_model=SearchResponse)
async def search(q: str = Query(..., min_length=1)):
    results = await asyncio.to_thread(itunes_search, q, 15)
    return SearchResponse(results=results)


@app.get("/api/mobile/chart", response_model=ChartResponse)
async def chart():
    cache = load_json(CHART_CACHE_FILE, {})
    if cache and time.time() - cache.get("ts", 0) < 6 * 3600:
        songs = [Song(**s) for s in cache.get("songs", [])]
        return ChartResponse(songs=songs)
    songs = await asyncio.to_thread(itunes_search, "top hits 2025", 30)
    save_json(CHART_CACHE_FILE, {
        "ts": time.time(),
        "songs": [s.model_dump() for s in songs]
    })
    return ChartResponse(songs=songs)


@app.get("/api/mobile/play", response_model=PlayResponse)
async def play(
    id: str = Query(...),
    artist: str = Query(...),
    title: str = Query(...),
    previous_song_id: Optional[str] = Query(default=None),
):
    """
    Get the streamable audio URL for a song via Invidious.
    Records to play history for recommendation engine.
    """
    await asyncio.to_thread(record_play, id, title, artist)
    stream_url = await asyncio.to_thread(
        get_stream_via_invidious, title, artist
    )
    return PlayResponse(stream_url=stream_url)


@app.get("/api/mobile/up_next", response_model=UpNextResponse)
async def up_next(song_id: str = Query(...), limit: int = Query(default=10)):
    """
    Queue suggestions based on the currently playing song.
    Uses iTunes search to find related tracks.
    """
    history: list = load_json(PLAY_HISTORY_FILE, [])
    song_info = next((h for h in history if h.get("id") == song_id), None)

    if song_info:
        query = f"{song_info['title']} {song_info['artist']}"
    else:
        query = "top hits 2025"

    songs = await asyncio.to_thread(itunes_search, query, limit + 1)
    # Exclude the current song itself
    songs = [s for s in songs if s.id != song_id][:limit]
    return UpNextResponse(songs=songs)


@app.get("/api/mobile/recommend", response_model=RecommendResponse)
async def recommend(song_id: str = Query(...)):
    """
    Grouped recommendations — shown on Android home screen after a song plays.
    Uses recently listened artists to personalise results.
    """
    history: list = load_json(PLAY_HISTORY_FILE, [])
    song_info = next((h for h in history if h.get("id") == song_id), None)

    queries = []
    if song_info:
        queries.append(f"{song_info['artist']} best songs")
    queries += [f"{a} popular songs" for a in recent_artists(3)]
    queries.append("trending music 2025")

    songs: list[Song] = []
    for q in queries[:3]:
        songs.extend(await asyncio.to_thread(itunes_search, q, 6))

    # Deduplicate, remove currently playing
    seen, unique = {song_id}, []
    for s in songs:
        if s.id not in seen:
            seen.add(s.id)
            unique.append(s)

    return RecommendResponse(recommendations=unique[:20])

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
