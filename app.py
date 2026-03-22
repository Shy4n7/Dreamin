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

# ── JioSaavn ───────────────────────────────────────────────────────────────────

def jiosaavn_search(query: str, limit: int = 15) -> list[Song]:
    """Search using JioSaavn API."""
    encoded = urllib.parse.quote(query)
    url = (
        f"https://www.jiosaavn.com/api.php"
        f"?__call=search.getResults"
        f"&_format=json"
        f"&_marker=0"
        f"&api_version=4"
        f"&ctx=web6dot0"
        f"&q={encoded}"
        f"&n={limit}"
        f"&p=1"
    )
    try:
        req = urllib.request.Request(url, headers={
            "User-Agent": "Mozilla/5.0"
        })
        with urllib.request.urlopen(req, timeout=10) as r:
            data = json.loads(r.read())
        songs = []
        for item in data.get("results", []):
            image = item.get("image", "").replace("150x150", "500x500")
            songs.append(Song(
                id=item.get("id", ""),
                title=item.get("title", ""),
                artist=item.get("more_info", {}).get("singers", ""),
                artwork_url=image,
                duration=int(item.get("more_info", {}).get("duration", 0)) * 1000,
            ))
        return songs
    except Exception as e:
        print(f"[jiosaavn_search] error: {e}")
        return []

def jiosaavn_stream(song_id: str) -> str:
    url1 = (
        f"https://www.jiosaavn.com/api.php"
        f"?__call=song.getDetails"
        f"&cc=in"
        f"&_marker=0"
        f"&_format=json"
        f"&pids={song_id}"
    )
    try:
        req1 = urllib.request.Request(url1, headers={
            "User-Agent": "Mozilla/5.0",
            "Referer": "https://www.jiosaavn.com/",
        })
        with urllib.request.urlopen(req1, timeout=10) as r:
            data1 = json.loads(r.read())

        song_data = data1.get(song_id, {})
        
        # encrypted_media_url is at TOP LEVEL, not inside more_info
        enc_url = song_data.get("encrypted_media_url", "")
        if not enc_url:
            raise ValueError(f"No encrypted_media_url found")

        # Step 2: Generate auth token
        enc_encoded = urllib.parse.quote(enc_url, safe="")
        url2 = (
            f"https://www.jiosaavn.com/api.php"
            f"?__call=song.generateAuthToken"
            f"&url={enc_encoded}"
            f"&bitrate=320"
            f"&api_version=4"
            f"&_format=json"
            f"&ctx=web6dot0"
            f"&_marker=0"
        )
        req2 = urllib.request.Request(url2, headers={
            "User-Agent": "Mozilla/5.0",
            "Referer": "https://www.jiosaavn.com/",
        })
        with urllib.request.urlopen(req2, timeout=10) as r:
            data2 = json.loads(r.read())

        auth_url = data2.get("auth_url", "")
        if not auth_url:
            raise ValueError(f"No auth_url. Response: {str(data2)[:300]}")
        return auth_url

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"Stream failed: {e}")

# ── Play history (used for recommendations) ─────────────────────────────────────

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
    results = await asyncio.to_thread(jiosaavn_search, q, 15)
    return SearchResponse(results=results)


@app.get("/api/mobile/chart", response_model=ChartResponse)
async def chart():
    cache = load_json(CHART_CACHE_FILE, {})
    if cache and time.time() - cache.get("ts", 0) < 6 * 3600:
        songs = [Song(**s) for s in cache.get("songs", [])]
        return ChartResponse(songs=songs)

    hindi, tamil = await asyncio.gather(
        asyncio.to_thread(jiosaavn_search, "top hindi songs 2025", 15),
        asyncio.to_thread(jiosaavn_search, "top tamil songs 2025", 15),
    )
    songs = hindi + tamil

    save_json(CHART_CACHE_FILE, {
        "ts": time.time(),
        "songs": [s.model_dump() for s in songs],
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
    Get the direct MP3 stream URL from JioSaavn.
    Records to play history for the recommendation engine.
    """
    await asyncio.to_thread(record_play, id, title, artist)
    stream_url = await asyncio.to_thread(jiosaavn_stream, id)
    return PlayResponse(stream_url=stream_url)


@app.get("/api/mobile/up_next", response_model=UpNextResponse)
async def up_next(song_id: str = Query(...), limit: int = Query(default=10)):
    """Queue suggestions based on the currently playing song."""
    history: list = load_json(PLAY_HISTORY_FILE, [])
    song_info = next((h for h in history if h.get("id") == song_id), None)

    if song_info:
        query = f"{song_info['title']} {song_info['artist']}"
    else:
        query = "top hits 2025"

    songs = await asyncio.to_thread(jiosaavn_search, query, limit + 1)
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
        songs.extend(await asyncio.to_thread(jiosaavn_search, q, 6))

    # Deduplicate, remove currently playing
    seen, unique = {song_id}, []
    for s in songs:
        if s.id not in seen:
            seen.add(s.id)
            unique.append(s)

    return RecommendResponse(recommendations=unique[:20])


@app.get("/api/debug/jiosaavn")
async def debug_jiosaavn():
    import urllib.request
    import json
    url = (
        "https://www.jiosaavn.com/api.php"
        "?__call=search.getResults"
        "&_format=json"
        "&_marker=0"
        "&api_version=4"
        "&ctx=web6dot0"
        "&query=taylor+swift"
        "&n=3"
        "&p=1"
    )
    try:
        req = urllib.request.Request(url, headers={
            "User-Agent": "Mozilla/5.0"
        })
        with urllib.request.urlopen(req, timeout=10) as r:
            raw = r.read()
            return {"status": "ok", "raw": raw.decode("utf-8")[:500]}
    except Exception as e:
        return {"status": "error", "detail": str(e)}


@app.get("/api/debug/stream")
async def debug_stream(song_id: str = Query(...)):
    url1 = (
        f"https://www.jiosaavn.com/api.php"
        f"?__call=song.getDetails"
        f"&cc=in"
        f"&_marker=0"
        f"&_format=json"
        f"&pids={song_id}"
    )
    try:
        req1 = urllib.request.Request(url1, headers={
            "User-Agent": "Mozilla/5.0",
            "Referer": "https://www.jiosaavn.com/",
        })
        with urllib.request.urlopen(req1, timeout=10) as r:
            data = json.loads(r.read())
        
        song_data = data.get(song_id, {})
        enc_url = song_data.get("encrypted_media_url", "")
        enc_drm = song_data.get("encrypted_drm_media_url", "")
        
        # Now try step 2 with the encrypted URL
        if enc_url:
            enc_encoded = urllib.parse.quote(enc_url, safe="")
            url2 = (
                f"https://www.jiosaavn.com/api.php"
                f"?__call=song.generateAuthToken"
                f"&url={enc_encoded}"
                f"&bitrate=320"
                f"&api_version=4"
                f"&_format=json"
                f"&ctx=web6dot0"
                f"&_marker=0"
            )
            req2 = urllib.request.Request(url2, headers={
                "User-Agent": "Mozilla/5.0",
                "Referer": "https://www.jiosaavn.com/",
            })
            with urllib.request.urlopen(req2, timeout=10) as r:
                data2 = json.loads(r.read())
            return {
                "enc_url": enc_url,
                "enc_drm": enc_drm,
                "step2_response": data2
            }
        return {"error": "no enc_url found", "keys": list(song_data.keys())}
    except Exception as e:
        return {"error": str(e)}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
