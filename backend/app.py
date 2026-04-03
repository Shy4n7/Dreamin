"""
Dreamin — FastAPI backend server
Mirrors every endpoint the Android app calls via Retrofit.
"""

from __future__ import annotations

import asyncio
import html
import json
import re
import urllib.request
import urllib.parse
import time
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel


CACHE_DIR = Path("song_cache")
DATA_DIR  = Path("data")
CACHE_DIR.mkdir(exist_ok=True)
DATA_DIR.mkdir(exist_ok=True)

PLAY_HISTORY_FILE = DATA_DIR / "play_history.json"


def load_json(path: Path, default):
    if path.exists():
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    return default

def save_json(path: Path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


class Song(BaseModel):
    id: str
    title: str
    artist: str
    artwork_url: str = ""
    duration: int = 0

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


def clean_title(raw: str) -> str:
    title = html.unescape(raw)
    title = re.sub(r'\s*\(?\s*[Ff]rom\s+["\u201c\u2018].*?["\u201d\u2019]?\s*\)?$', '', title).strip()
    return title


def jiosaavn_search(query: str, limit: int = 15, page: int = 1) -> list[Song]:
    encoded = urllib.parse.quote(query)
    url = (
        f"https://www.jiosaavn.com/api.php"
        f"?__call=search.getResults"
        f"&_format=json&_marker=0&api_version=4&ctx=web6dot0"
        f"&q={encoded}&n={limit}&p={page}"
    )
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=10) as r:
            data = json.loads(r.read())
        songs = []
        for item in data.get("results", []):
            image = item.get("image", "").replace("150x150", "500x500")
            songs.append(Song(
                id=item.get("id", ""),
                title=clean_title(item.get("title", "")),
                artist=html.unescape(item.get("more_info", {}).get("singers", "")),
                artwork_url=image,
                duration=int(item.get("more_info", {}).get("duration", 0)) * 1000,
            ))
        return songs
    except Exception as e:
        print(f"[jiosaavn_search] error: {e}")
        return []


def jiosaavn_song_details(song_id: str) -> dict:
    """Fetch full song metadata from JioSaavn including language, genre, artists."""
    url = (
        f"https://www.jiosaavn.com/api.php"
        f"?__call=song.getDetails&cc=in&_marker=0&_format=json&pids={song_id}"
    )
    try:
        req = urllib.request.Request(url, headers={
            "User-Agent": "Mozilla/5.0",
            "Referer": "https://www.jiosaavn.com/",
        })
        with urllib.request.urlopen(req, timeout=10) as r:
            data = json.loads(r.read())
        return data.get(song_id, {})
    except Exception:
        return {}


def detect_language(artist: str, title: str, history: list[dict]) -> str:
    """
    Infer language from play history pattern.
    Returns a language string usable in JioSaavn queries.
    """
    # Count languages from recent play history (stored entries may have language tag)
    lang_counts: dict[str, int] = {}
    for entry in history[-30:]:
        lang = entry.get("language", "")
        if lang:
            lang_counts[lang] = lang_counts.get(lang, 0) + 1

    if lang_counts:
        return max(lang_counts, key=lambda k: lang_counts[k])

    # Heuristic: script-based detection from artist/title chars
    text = artist + title
    if re.search(r'[\u0B80-\u0BFF]', text):
        return "tamil"
    if re.search(r'[\u0C00-\u0C7F]', text):
        return "telugu"
    if re.search(r'[\u0D00-\u0D7F]', text):
        return "malayalam"
    if re.search(r'[\u0900-\u097F]', text):
        return "hindi"
    return ""


def build_queue_queries(song_details: dict, artist: str, language: str) -> list[str]:
    """
    Build a prioritised list of search queries for queue allocation.
    Never uses song title — only artist, genre, and language.
    Order: genre+language → artist+language → featured+language → language fallbacks.
    """
    queries: list[str] = []

    raw_lang  = song_details.get("language", language).lower().strip()
    raw_genre = html.unescape(song_details.get("more_info", {}).get("genres", "") or "")
    primary   = html.unescape(song_details.get("more_info", {}).get("primary_artists", "") or artist)
    featured  = html.unescape(song_details.get("more_info", {}).get("featured_artists", "") or "")

    primary_first  = primary.split(",")[0].strip()
    featured_first = featured.split(",")[0].strip() if featured else ""

    # 1. Genre + language — most specific vibe match
    if raw_genre and raw_lang:
        queries.append(f"best {raw_genre} {raw_lang} songs")
    elif raw_genre:
        queries.append(f"best {raw_genre} songs")

    # 2. Artist + language — same artist, stays in language
    if primary_first and raw_lang:
        queries.append(f"{primary_first} {raw_lang} songs")
    elif primary_first:
        queries.append(f"{primary_first} songs")

    # 3. Featured artist + language (introduces variety while keeping language)
    if featured_first and featured_first != primary_first:
        if raw_lang:
            queries.append(f"{featured_first} {raw_lang} songs")
        else:
            queries.append(f"{featured_first} songs")

    # 4. Genre alone (broader but still genre-consistent)
    if raw_genre:
        queries.append(f"top {raw_genre} hits")

    # 5. Language-level fallbacks — keeps the session in same language
    if raw_lang:
        queries.append(f"popular {raw_lang} songs 2024")
        queries.append(f"top {raw_lang} hits")

    return queries


def jiosaavn_stream(song_id: str) -> str:
    url1 = (
        f"https://www.jiosaavn.com/api.php"
        f"?__call=song.getDetails&cc=in&_marker=0&_format=json&pids={song_id}"
    )
    try:
        req1 = urllib.request.Request(url1, headers={
            "User-Agent": "Mozilla/5.0",
            "Referer": "https://www.jiosaavn.com/",
        })
        with urllib.request.urlopen(req1, timeout=10) as r:
            data1 = json.loads(r.read())

        song_data = data1.get(song_id, {})
        enc_url = song_data.get("encrypted_media_url", "")
        if not enc_url:
            raise ValueError("No encrypted_media_url found")

        enc_encoded = urllib.parse.quote(enc_url, safe="")
        url2 = (
            f"https://www.jiosaavn.com/api.php"
            f"?__call=song.generateAuthToken"
            f"&url={enc_encoded}&bitrate=320&api_version=4"
            f"&_format=json&ctx=web6dot0&_marker=0"
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


def record_play(song_id: str, title: str, artist: str, language: str = ""):
    history: list = load_json(PLAY_HISTORY_FILE, [])
    history.append({
        "id": song_id,
        "title": title,
        "artist": artist,
        "language": language,
        "ts": time.time(),
    })
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


app = FastAPI(title="Dreamin API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/api/mobile/health")
async def health():
    return {"status": "ok", "server": "Dreamin"}


@app.get("/api/mobile/search", response_model=SearchResponse)
async def search(
    q: str = Query(..., min_length=1),
    page: int = Query(default=1, ge=1),
    limit: int = Query(default=15, ge=1, le=50),
):
    results = await asyncio.to_thread(jiosaavn_search, q, limit, page)
    return SearchResponse(results=results)


LANGUAGE_QUERIES: dict[str, str] = {
    "tamil":     "top tamil songs 2025",
    "hindi":     "top hindi songs 2025",
    "english":   "top english songs 2025",
    "telugu":    "top telugu songs 2025",
    "malayalam": "top malayalam songs 2025",
}

@app.get("/api/mobile/chart", response_model=ChartResponse)
async def chart(language: str = Query(default="tamil")):
    lang = language.lower()
    cache_file = DATA_DIR / f"chart_cache_{lang}.json"
    cache = load_json(cache_file, {})
    if cache and time.time() - cache.get("ts", 0) < 6 * 3600:
        songs = [Song(**s) for s in cache.get("songs", [])]
        return ChartResponse(songs=songs)

    query = LANGUAGE_QUERIES.get(lang, f"top {lang} songs 2025")
    songs = await asyncio.to_thread(jiosaavn_search, query, 30)

    save_json(cache_file, {"ts": time.time(), "songs": [s.model_dump() for s in songs]})
    return ChartResponse(songs=songs)


@app.get("/api/mobile/play", response_model=PlayResponse)
async def play(
    id: str = Query(...),
    artist: str = Query(...),
    title: str = Query(...),
):
    # Fetch song details to capture language for history
    song_data = await asyncio.to_thread(jiosaavn_song_details, id)
    language = song_data.get("language", "").lower().strip()

    await asyncio.to_thread(record_play, id, title, artist, language)
    stream_url = await asyncio.to_thread(jiosaavn_stream, id)
    return PlayResponse(stream_url=stream_url)


@app.get("/api/mobile/up_next", response_model=UpNextResponse)
async def up_next(song_id: str = Query(...), limit: int = Query(default=10)):
    """
    Smart queue allocation: same language → same artist vibe → same genre.
    Fetches full song metadata to drive language/genre-aware query building.
    """
    history: list = load_json(PLAY_HISTORY_FILE, [])
    history_entry = next((h for h in reversed(history) if h.get("id") == song_id), None)

    artist = history_entry.get("artist", "") if history_entry else ""
    title  = history_entry.get("title", "")  if history_entry else ""
    stored_lang = history_entry.get("language", "") if history_entry else ""

    # Always fetch full details for language/genre metadata
    song_data = await asyncio.to_thread(jiosaavn_song_details, song_id)

    if not artist:
        artist = html.unescape(song_data.get("more_info", {}).get("primary_artists", ""))
    if not title:
        title = clean_title(song_data.get("title", ""))

    language = song_data.get("language", stored_lang).lower().strip()
    if not language:
        language = detect_language(artist, title, history)

    queries = build_queue_queries(song_data, artist, language)

    # Gather results from top queries, deduplicate, exclude current song
    seen_ids: set[str] = {song_id}
    results: list[Song] = []

    for q in queries:
        if len(results) >= limit:
            break
        batch = await asyncio.to_thread(jiosaavn_search, q, limit)
        for s in batch:
            if s.id not in seen_ids and len(results) < limit:
                seen_ids.add(s.id)
                results.append(s)

    return UpNextResponse(songs=results)


@app.get("/api/mobile/recommend", response_model=RecommendResponse)
async def recommend(song_id: str = Query(...)):
    """
    Personalised recommendations: language-aware, vibe-consistent.
    Uses play history language pattern + artist to build diverse but cohesive results.
    """
    history: list = load_json(PLAY_HISTORY_FILE, [])
    history_entry = next((h for h in reversed(history) if h.get("id") == song_id), None)

    artist = history_entry.get("artist", "") if history_entry else ""
    title  = history_entry.get("title", "")  if history_entry else ""
    stored_lang = history_entry.get("language", "") if history_entry else ""

    song_data = await asyncio.to_thread(jiosaavn_song_details, song_id)
    language = song_data.get("language", stored_lang).lower().strip()
    if not language:
        language = detect_language(artist, title, history)

    if not artist:
        artist = html.unescape(song_data.get("more_info", {}).get("primary_artists", ""))

    primary_first = artist.split(",")[0].strip()
    raw_genre = html.unescape(song_data.get("more_info", {}).get("genres", "") or "")

    queries: list[str] = []

    # Same artist, same language
    if primary_first and language:
        queries.append(f"{primary_first} {language} songs")

    # Recent artists in same language
    for a in recent_artists(3):
        if language:
            queries.append(f"{a} {language} songs")
        else:
            queries.append(f"{a} popular songs")

    # Genre-based if available
    if raw_genre and language:
        queries.append(f"{raw_genre} {language} songs")

    # Language fallback
    if language:
        queries.append(f"top {language} songs 2024")

    queries.append("trending music 2025")

    seen_ids: set[str] = {song_id}
    songs: list[Song] = []

    for q in queries[:5]:
        batch = await asyncio.to_thread(jiosaavn_search, q, 6)
        for s in batch:
            if s.id not in seen_ids:
                seen_ids.add(s.id)
                songs.append(s)

    return RecommendResponse(recommendations=songs[:20])


@app.get("/api/debug/stream")
async def debug_stream(song_id: str = Query(...)):
    song_data = await asyncio.to_thread(jiosaavn_song_details, song_id)
    enc_url = song_data.get("encrypted_media_url", "")
    if enc_url:
        enc_encoded = urllib.parse.quote(enc_url, safe="")
        url2 = (
            f"https://www.jiosaavn.com/api.php"
            f"?__call=song.generateAuthToken"
            f"&url={enc_encoded}&bitrate=320&api_version=4"
            f"&_format=json&ctx=web6dot0&_marker=0"
        )
        req2 = urllib.request.Request(url2, headers={
            "User-Agent": "Mozilla/5.0",
            "Referer": "https://www.jiosaavn.com/",
        })
        with urllib.request.urlopen(req2, timeout=10) as r:
            data2 = json.loads(r.read())
        return {"enc_url": enc_url, "step2_response": data2}
    return {"error": "no enc_url found", "keys": list(song_data.keys())}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
