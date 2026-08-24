"""
Dreamin — FastAPI backend server
Mirrors every endpoint the Android app calls via Retrofit.
"""

from __future__ import annotations

import asyncio
import html
import json
import os
import re
import tempfile
import threading
import urllib.request
import urllib.parse
import time
import datetime
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException, Query, Header
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse
from pydantic import BaseModel
from dotenv import load_dotenv

load_dotenv()


CACHE_DIR = Path("song_cache")
DATA_DIR  = Path("data")
CACHE_DIR.mkdir(exist_ok=True)
DATA_DIR.mkdir(exist_ok=True)

PLAY_HISTORY_FILE = DATA_DIR / "play_history.json"
USERS_FILE        = DATA_DIR / "users.json"

ADMIN_TOKEN = os.environ.get("DREAMIN_ADMIN_TOKEN", "shyan-admin-2025")

# ── In-memory caches ────────────────────────────────────────────────────────

# up_next cache: song_id → {"ts": float, "songs": list[dict]}
_upnext_cache: dict[str, dict] = {}
_UPNEXT_TTL = 3600  # 1 hour

# song details cache: song_id → dict  (populated by /play, reused by /up_next)
_song_details_cache: dict[str, dict] = {}
_DETAILS_TTL = 7200  # 2 hours; keyed by song_id, value = {"ts": float, "data": dict}

# chart background refresh lock per language
_chart_refresh_lock: dict[str, asyncio.Lock] = {}
_CHART_TTL = 6 * 3600  # 6 hours; background refresh starts at 80% of TTL

# play history write lock (prevents race conditions with concurrent requests)
_history_lock = threading.Lock()


# ── Helpers ──────────────────────────────────────────────────────────────────

def load_json(path: Path, default):
    if path.exists():
        try:
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f)
        except (json.JSONDecodeError, OSError):
            return default
    return default


def save_json_atomic(path: Path, data):
    """Write JSON atomically via temp-file + rename to prevent corruption."""
    dir_ = path.parent
    with tempfile.NamedTemporaryFile(
        mode="w", encoding="utf-8", dir=dir_, delete=False, suffix=".tmp"
    ) as tmp:
        json.dump(data, tmp, ensure_ascii=False, indent=2)
        tmp_path = tmp.name
    os.replace(tmp_path, path)


def save_json(path: Path, data):
    save_json_atomic(path, data)


# ── Pydantic models ──────────────────────────────────────────────────────────

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
    stream_url: Optional[str] = None
    proxy_url: Optional[str] = None

class UpNextResponse(BaseModel):
    songs: list[Song] = []

class RecommendResponse(BaseModel):
    recommendations: list[Song] = []

class RegisterRequest(BaseModel):
    name: str
    device_id: str = ""


# ── Title / metadata helpers ─────────────────────────────────────────────────

def clean_title(raw: str) -> str:
    title = html.unescape(raw or "")
    title = re.sub(r'\s*\(?\s*[Ff]rom\s+["\u201c\u2018].*?["\u201d\u2019]?\s*\)?$', '', title).strip()
    return title


def normalize_title(raw_title: str) -> str:
    """Normalize song title to detect duplicates across language remakes or editions."""
    t = clean_title(raw_title).lower()
    t = re.sub(r'[\(\[\{].*?[\)\]\}]', '', t)
    t = re.sub(r'[^a-z0-9]', '', t)
    return t.strip()


def clean_artwork_url(url: str) -> str:
    """Upgrade low-res thumbnails (50x50, 150x150) to crisp 500x500 https images."""
    if not url:
        return ""
    cleaned = re.sub(r'_\d+x\d+\.', '_500x500.', url)
    cleaned = cleaned.replace("50x50", "500x500").replace("150x150", "500x500")
    if cleaned.startswith("http://"):
        cleaned = "https://" + cleaned[7:]
    return cleaned


NOISE_KEYWORDS = {
    "remix", "8d", "8d audio", "bass boosted", "slowed", "reverb", "cover",
    "lofi", "lo-fi", "flip", "status", "dialogue", "teaser", "trailer",
    "jukebox", "mashup", "full audio", "full song", "bgm", "ringtone",
    "theme", "promo", "whatsapp", "sped up", "speed up", "karaoke"
}


def is_noise_track(title: str) -> bool:
    """Filter out amateur remixes, bass-boosted tracks, status clips, and teasers."""
    t_lower = title.lower()
    for kw in NOISE_KEYWORDS:
        if re.search(r'(?i)\b' + re.escape(kw) + r'\b', t_lower):
            return True
    return False


COMPILATION_KEYWORDS = {
    "best of", "this is", "top ", "hits", "vol.", "vol ", "volume",
    "love notes", "collection", "playlist", "raga collective", "kondattam",
    "selected", "radio", "superhit", "compilation", "greatest hits",
    "evergreen", "melody", "melodies", "latest", "essential", "party",
    "workout", "for the road", "romance", "mashup", "area boys",
    "konjam", "thamizh music", "kondattam"
}


def extract_movie_from_title(title: str) -> str:
    m = re.search(r'(?i)\(?\s*from\s+["\'\u201c\u2018](.*?)["\'\u201d\u2019]\s*\)?', title)
    return m.group(1).strip().lower() if m else ""


def is_compilation_album(album_name: str) -> bool:
    if not album_name:
        return False
    alb = album_name.lower()
    return any(kw in alb for kw in COMPILATION_KEYWORDS)


def resolve_original_artwork(title: str, current_image: str, album: str) -> str:
    """Resolve genuine movie OST poster when song is bundled in a generic compilation album."""
    if not is_compilation_album(album):
        return current_image
    try:
        clean_q = re.sub(r'[\(\[\{].*?[\)\]\}]', '', title).strip() or title
        q = urllib.parse.quote(clean_title(clean_q))
        url = (
            f"https://www.jiosaavn.com/api.php"
            f"?__call=search.getResults"
            f"&_format=json&_marker=0&api_version=4&ctx=web6dot0"
            f"&q={q}&n=10&p=1"
        )
        req = urllib.request.Request(url, headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Referer": "https://www.jiosaavn.com/"
        })
        with urllib.request.urlopen(req, timeout=5) as r:
            data = json.loads(r.read())
        items = data.get("results", [])
        
        movie_detected = ""
        for it in items:
            raw_t = html.unescape(it.get("title", ""))
            m = extract_movie_from_title(raw_t)
            if m:
                movie_detected = m
                break

        def score_item(it):
            alb_name = it.get("more_info", {}).get("album", "").strip().lower()
            if movie_detected and alb_name == movie_detected:
                return 0
            if not is_compilation_album(alb_name):
                return 1
            return 2

        if items:
            best = min(items, key=score_item)
            best_alb = best.get("more_info", {}).get("album", "")
            if not is_compilation_album(best_alb) or (movie_detected and best_alb.lower() == movie_detected):
                return clean_artwork_url(best.get("image", ""))
    except Exception as e:
        print(f"[resolve_artwork] Error for {title}: {e}")
    return current_image


def extract_artist(more_info: dict) -> str:
    """Extract artist name — handles both old (singers string) and new (artistMap array) API shapes."""
    artist_map = more_info.get("artistMap", {})
    primary = artist_map.get("primary_artists", [])
    if primary and isinstance(primary, list):
        return ", ".join(a["name"] for a in primary if a.get("name"))

    singers = more_info.get("singers", "")
    if singers:
        return html.unescape(singers)

    featured = artist_map.get("featured_artists", [])
    if featured and isinstance(featured, list):
        return ", ".join(a["name"] for a in featured if a.get("name"))

    return ""


def jiosaavn_search(query: str, limit: int = 15, page: int = 1, target_language: str = "") -> list[Song]:
    encoded = urllib.parse.quote(query)
    url = (
        f"https://www.jiosaavn.com/api.php"
        f"?__call=search.getResults"
        f"&_format=json&_marker=0&api_version=4&ctx=web6dot0"
        f"&q={encoded}&n={limit}&p={page}"
    )
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Linux; Android 14)"})
            with urllib.request.urlopen(req, timeout=10) as r:
                data = json.loads(r.read())
            results = data.get("results", [])
            if not results and attempt < 2:
                time.sleep(0.4)
                continue

            # Group candidate releases to prioritize original OST album
            grouped: dict[str, list[dict]] = {}
            for item in results:
                raw_title = item.get("title", "")
                title = clean_title(raw_title)
                more = item.get("more_info", {})
                duration_sec = int(more.get("duration", 0) or 0)
                item_lang = item.get("language", more.get("language", "")).lower().strip()

                # Filter noise
                if is_noise_track(title):
                    continue

                # Filter duration anomalies (too short / full movie jukeboxes)
                if duration_sec > 0 and (duration_sec < 75 or duration_sec > 540):
                    continue

                # Filter language if target specified
                if target_language and item_lang and item_lang != target_language.lower():
                    continue

                norm_key = normalize_title(title)
                if norm_key not in grouped:
                    grouped[norm_key] = []
                grouped[norm_key].append(item)

            songs = []
            for norm_key, items in grouped.items():
                movie_detected = ""
                for it in items:
                    m = extract_movie_from_title(it.get("title", ""))
                    if m:
                        movie_detected = m
                        break

                def score_group_item(it):
                    alb_name = it.get("more_info", {}).get("album", "").strip().lower()
                    if movie_detected and alb_name == movie_detected:
                        return 0
                    if not is_compilation_album(alb_name):
                        return 1
                    return 2

                best_item = min(items, key=score_group_item)
                raw_title = best_item.get("title", "")
                title = clean_title(raw_title)
                more = best_item.get("more_info", {})
                duration_sec = int(more.get("duration", 0) or 0)
                alb = more.get("album", "")
                image = clean_artwork_url(best_item.get("image", ""))
                if is_compilation_album(alb):
                    image = resolve_original_artwork(title, image, alb)

                songs.append(Song(
                    id=best_item.get("id", ""),
                    title=title,
                    artist=extract_artist(more),
                    artwork_url=image,
                    duration=duration_sec * 1000,
                ))
            return songs[:limit]
        except Exception as e:
            print(f"[jiosaavn_search] attempt {attempt + 1} error: {e}")
            if attempt < 2:
                time.sleep(0.4)
    return []


def _fetch_song_details_raw(song_id: str) -> dict:
    """Network fetch — bypasses cache."""
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


def jiosaavn_song_details(song_id: str) -> dict:
    """
    Return song details from in-memory cache if fresh, otherwise fetch.
    Cache is populated by /play so /up_next avoids redundant network calls
    when the user recently played the song.
    """
    entry = _song_details_cache.get(song_id)
    if entry and time.time() - entry["ts"] < _DETAILS_TTL:
        return entry["data"]
    data = _fetch_song_details_raw(song_id)
    _song_details_cache[song_id] = {"ts": time.time(), "data": data}
    return data


def detect_language(artist: str, title: str, history: list[dict]) -> str:
    lang_counts: dict[str, int] = {}
    for entry in history[-30:]:
        lang = entry.get("language", "")
        if lang:
            lang_counts[lang] = lang_counts.get(lang, 0) + 1

    if lang_counts:
        return max(lang_counts, key=lambda k: lang_counts[k])

    text = artist + title
    if re.search(r'[\u0B80-\u0BFF]', text):
        return "tamil"
    if re.search(r'[\u0C00-\u0C7F]', text):
        return "telugu"
    if re.search(r'[\u0D00-\u0D7F]', text):
        return "malayalam"
    if re.search(r'[\u0900-\u097F]', text):
        return "hindi"
    return "tamil"


def build_queue_queries(
    song_details: dict, artist: str, language: str, recent_artists_list: list[str]
) -> list[str]:
    queries: list[str] = []

    more      = song_details.get("more_info", {})
    raw_lang  = song_details.get("language", language).lower().strip() or language or "tamil"
    raw_genre = html.unescape(more.get("genres", "") or "")
    album     = html.unescape(more.get("album", "") or "")
    primary   = html.unescape(
        song_details.get("primary_artists", "")
        or more.get("primary_artists", "")
        or artist
    )
    music_dir = html.unescape(more.get("music", "") or "")

    primary_first  = primary.split(",")[0].strip()
    music_first    = music_dir.split(",")[0].strip() if music_dir else ""

    # 1. Album / Movie affinity
    if album and raw_lang:
        queries.append(f"{album} {raw_lang} songs")

    # 2. Composer / Music Director hits
    if music_first and raw_lang:
        queries.append(f"{music_first} {raw_lang} hits")
    elif primary_first and raw_lang:
        queries.append(f"{primary_first} {raw_lang} hits")

    # 3. Genre + Language mix
    if raw_genre and raw_lang:
        queries.append(f"{raw_genre} {raw_lang} songs")

    # 4. Regional Top Hits in exact language
    if raw_lang:
        queries.append(f"top {raw_lang} songs 2025")
        queries.append(f"best {raw_lang} melody songs")

    # 5. Recent favorites in the same language
    for a in recent_artists_list[:3]:
        if a != primary_first and raw_lang:
            queries.append(f"{a} {raw_lang} songs")

    return queries


def jiosaavn_stream(song_id: str, title: str = "", artist: str = "") -> str:
    enc_url = ""
    try:
        url1 = (
            f"https://www.jiosaavn.com/api.php"
            f"?__call=song.getDetails&cc=in&_marker=0&_format=json&pids={song_id}"
        )
        req1 = urllib.request.Request(url1, headers={
            "User-Agent": "Mozilla/5.0",
            "Referer": "https://www.jiosaavn.com/",
        })
        with urllib.request.urlopen(req1, timeout=8) as r:
            data1 = json.loads(r.read())

        song_data = data1.get(song_id, {})
        enc_url = song_data.get("encrypted_media_url", "")
    except Exception:
        pass

    if not enc_url and title:
        try:
            clean_title = re.sub(r'[\(\[].*?[\)\]]', '', title).strip()
            q = urllib.parse.quote(f"{clean_title} {artist}".strip())
            s_url = f"https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&cc=in&p=1&n=5&q={q}"
            req_s = urllib.request.Request(s_url, headers={"User-Agent": "Mozilla/5.0", "Referer": "https://www.jiosaavn.com/"})
            with urllib.request.urlopen(req_s, timeout=8) as r:
                s_data = json.loads(r.read())
            results = s_data.get("results", [])
            for res in results:
                cand_enc = res.get("encrypted_media_url", "")
                if cand_enc:
                    enc_url = cand_enc
                    break
        except Exception:
            pass

    if not enc_url:
        raise HTTPException(status_code=502, detail=f"No encrypted_media_url found for {song_id}")

    try:
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
        with urllib.request.urlopen(req2, timeout=8) as r:
            data2 = json.loads(r.read())

        auth_url = data2.get("auth_url", "")
        if not auth_url:
            raise ValueError(f"No auth_url: {str(data2)[:200]}")
        return auth_url
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"Stream failed: {e}")


def record_play(song_id: str, title: str, artist: str, language: str = ""):
    with _history_lock:
        history: list = load_json(PLAY_HISTORY_FILE, [])
        history.append({
            "id": song_id,
            "title": title,
            "artist": artist,
            "language": language,
            "ts": time.time(),
        })
        history = history[-200:]
        save_json_atomic(PLAY_HISTORY_FILE, history)


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


# ── FastAPI app ──────────────────────────────────────────────────────────────

app = FastAPI(title="Dreamin API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/")
async def root():
    return {"status": "ok", "server": "Dreamin", "version": "1.1.2-IST"}


@app.get("/api/mobile/health")
async def health():
    return {"status": "ok", "server": "Dreamin"}


@app.post("/api/mobile/register")
async def register(req: RegisterRequest):
    name = req.name.strip()
    if not name:
        raise HTTPException(status_code=400, detail="Name cannot be empty")
    users: list[dict] = load_json(USERS_FILE, [])
    entry = {
        "name": name,
        "device_id": req.device_id,
        "registered_at": datetime.datetime.now(datetime.timezone.utc).isoformat()
    }
    # Update existing device or append new
    existing = next((u for u in users if u.get("device_id") == req.device_id and req.device_id), None)
    if existing:
        existing["name"] = name
        existing["updated_at"] = entry["registered_at"]
    else:
        users.append(entry)
    save_json(USERS_FILE, users)
    return {"status": "ok"}


@app.get("/admin/users", response_class=HTMLResponse)
async def admin_users(token: str = Query(...)):
    if token != ADMIN_TOKEN:
        raise HTTPException(status_code=403, detail="Forbidden")

    IST = datetime.timezone(datetime.timedelta(hours=5, minutes=30))

    def fmt_ts(ts_val) -> str:
        if not ts_val:
            return "—"
        try:
            # Handle timestamps (floats/ints)
            if isinstance(ts_val, (int, float)):
                return datetime.datetime.fromtimestamp(ts_val, tz=IST).strftime("%d %b %Y, %I:%M %p IST")
            
            # Handle ISO strings (handle 'Z' for older Python versions)
            ts_str = str(ts_val).replace('Z', '+00:00')
            return datetime.datetime.fromisoformat(ts_str).astimezone(IST).strftime("%d %b %Y, %I:%M %p IST")
        except Exception as e:
            return str(ts_val)

    users: list[dict] = load_json(USERS_FILE, [])
    user_rows = ""
    for u in reversed(users):
        ts = u.get("updated_at") or u.get("registered_at", "")
        dt = fmt_ts(ts)
        device = u.get("device_id", "")
        device_display = (device[:16] + "…") if len(device) > 16 else (device or "—")
        user_rows += f"""
        <tr>
          <td>{html.escape(u.get("name", ""))}</td>
          <td style="font-family:monospace;font-size:12px">{html.escape(device_display)}</td>
          <td>{dt}</td>
        </tr>"""

    history: list[dict] = load_json(PLAY_HISTORY_FILE, [])
    stream_rows = ""
    for entry in reversed(history[-50:]):
        dt = fmt_ts(entry.get("ts"))
        lang = entry.get("language", "—") or "—"
        stream_rows += f"""
        <tr>
          <td>{html.escape(entry.get("title", "—"))}</td>
          <td>{html.escape(entry.get("artist", "—"))}</td>
          <td><span class="lang-badge">{html.escape(lang)}</span></td>
          <td>{dt}</td>
        </tr>"""

    page = f"""<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Dreamin — Admin</title>
  <style>
    * {{ box-sizing: border-box; }}
    body {{ font-family: -apple-system, sans-serif; background: #0a0a0a; color: #fff; padding: 24px; margin: 0; }}
    h1 {{ color: #aba3ff; margin: 0 0 4px; }}
    h2 {{ color: #aba3ff; margin: 32px 0 12px; font-size: 16px; text-transform: uppercase; letter-spacing: .08em; }}
    .meta {{ color: #888; margin: 0 0 8px; font-size: 14px; }}
    table {{ border-collapse: collapse; width: 100%; max-width: 800px; margin-bottom: 8px; }}
    th {{ text-align: left; color: #aba3ff; padding: 8px 16px; border-bottom: 1px solid #333; font-size: 12px; text-transform: uppercase; letter-spacing: .05em; }}
    td {{ padding: 10px 16px; border-bottom: 1px solid #1a1a1a; font-size: 14px; }}
    tr:hover td {{ background: #111; }}
    .lang-badge {{ background: #1a1a2e; color: #aba3ff; border-radius: 4px; padding: 2px 8px; font-size: 11px; text-transform: capitalize; }}
  </style>
</head>
<body>
  <h1>Dreamin</h1>
  <p class="meta">Admin dashboard</p>

  <h2>Users <span style="color:#888;font-weight:400;font-size:13px">({len(users)})</span></h2>
  <table>
    <thead><tr><th>Name</th><th>Device ID</th><th>Last Seen</th></tr></thead>
    <tbody>{user_rows}</tbody>
  </table>

  <h2>Recent Streams <span style="color:#888;font-weight:400;font-size:13px">(last 50)</span></h2>
  <table>
    <thead><tr><th>Title</th><th>Artist</th><th>Language</th><th>Played At</th></tr></thead>
    <tbody>{stream_rows}</tbody>
  </table>
</body>
</html>"""
    return HTMLResponse(content=page)


@app.get("/api/mobile/search", response_model=SearchResponse)
async def search(
    q: str = Query(..., min_length=1),
    page: int = Query(default=1, ge=1),
    limit: int = Query(default=15, ge=1, le=50),
):
    results = await asyncio.to_thread(jiosaavn_search, q, limit, page)
    return SearchResponse(results=results)


PLAYLIST_CHARTS: dict[str, str] = {
    "tamil":     "1268500351",  # Trending Tamil Songs
    "hindi":     "1134543272",  # India Superhits Top 50
    "english":   "946682072",   # Top English Hits
    "telugu":    "1073873492",  # Trending Telugu
    "malayalam": "1134548194",  # Trending Malayalam
}

LANGUAGE_QUERIES: dict[str, str] = {
    "tamil":     "trending tamil songs 2025",
    "hindi":     "trending hindi songs 2025",
    "english":   "trending english hits 2025",
    "telugu":    "trending telugu songs 2025",
    "malayalam": "trending malayalam songs 2025",
}


def jiosaavn_playlist_songs(list_id: str, limit: int = 35) -> list[Song]:
    url = f"https://www.jiosaavn.com/api.php?__call=playlist.getDetails&_format=json&cc=in&_marker=0&listid={list_id}"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
    try:
        with urllib.request.urlopen(req, timeout=7) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            songs_raw = data.get("songs", data.get("list", []))
            songs = []
            for s in songs_raw:
                song_id = s.get("id")
                title = html.unescape(s.get("song") or s.get("title") or "")
                more = s.get("more_info", {})
                duration = int(s.get("duration") or more.get("duration") or 0) * 1000
                artist = html.unescape(s.get("primary_artists") or more.get("singers") or "")
                artwork = clean_artwork_url(s.get("image") or more.get("image") or "")
                if song_id and title:
                    songs.append(Song(
                        id=song_id,
                        title=title,
                        artist=artist,
                        artwork_url=artwork,
                        duration=duration
                    ))
            return songs[:limit]
    except Exception as e:
        print(f"[jiosaavn_playlist_songs] error for {list_id}: {e}")
        return []


def fetch_chart_songs(lang: str, limit: int = 30) -> list[Song]:
    list_id = PLAYLIST_CHARTS.get(lang)
    if list_id:
        songs = jiosaavn_playlist_songs(list_id, limit=limit)
        if songs:
            return songs
    query = LANGUAGE_QUERIES.get(lang, f"trending {lang} songs 2025")
    return jiosaavn_search(query, limit)


async def _refresh_chart_cache(lang: str, cache_file: Path):
    """Background task: fetch fresh chart data and write to disk cache."""
    try:
        songs = await asyncio.to_thread(fetch_chart_songs, lang, 30)
        if songs:
            save_json_atomic(cache_file, {"ts": time.time(), "songs": [s.model_dump() for s in songs]})
    except Exception as e:
        print(f"[chart_refresh] background refresh failed for {lang}: {e}")


@app.get("/api/mobile/chart", response_model=ChartResponse)
async def chart(language: str = Query(default="tamil")):
    lang = language.lower()
    cache_file = DATA_DIR / f"chart_cache_{lang}.json"
    cache = load_json(cache_file, {})
    now = time.time()
    age = now - cache.get("ts", 0)

    # Check if cached songs are outdated compilation lists (e.g. containing Don'u)
    cached_raw = cache.get("songs", [])
    has_stale_data = any("don'u" in (s.get("title", "")).lower() for s in cached_raw)

    if cache and age < _CHART_TTL and not has_stale_data:
        songs = [Song(**s) for s in cached_raw]

        if age > _CHART_TTL * 0.8:
            if lang not in _chart_refresh_lock:
                _chart_refresh_lock[lang] = asyncio.Lock()
            lock = _chart_refresh_lock[lang]
            if not lock.locked():
                asyncio.create_task(
                    _run_with_lock(lock, _refresh_chart_cache(lang, cache_file))
                )

        return ChartResponse(songs=songs)

    # Cache expired, missing, or stale — fetch fresh trending chart
    songs = await asyncio.to_thread(fetch_chart_songs, lang, 30)
    save_json_atomic(cache_file, {"ts": now, "songs": [s.model_dump() for s in songs]})
    return ChartResponse(songs=songs)


async def _run_with_lock(lock: asyncio.Lock, coro):
    """Run a coroutine under a lock — used to serialise background refreshes."""
    if lock.locked():
        return
    async with lock:
        await coro


@app.get("/api/mobile/proxy")
async def stream_proxy(url: str = Query(...)):
    """Relays audio from CDNs to bypass client-side network restrictions."""
    user_agent = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"
    referer = "https://www.jiosaavn.com/"
    
    try:
        # We use a stream-capable request
        req = urllib.request.Request(url, headers={
            "User-Agent": user_agent,
            "Referer": referer,
        })
        
        def iter_content():
            with urllib.request.urlopen(req, timeout=15) as r:
                # Basic chunked streaming
                while True:
                    chunk = r.read(65536) # 64KB
                    if not chunk:
                        break
                    yield chunk

        from fastapi.responses import StreamingResponse
        # Check if it's an mp4 or m4a
        media_type = "audio/mpeg"
        if ".mp4" in url or ".m4a" in url:
            media_type = "audio/mp4"
            
        return StreamingResponse(iter_content(), media_type=media_type)

    except Exception as e:
        raise HTTPException(status_code=502, detail=f"Proxy failed: {e}")


@app.get("/api/mobile/play", response_model=PlayResponse)
async def play(
    id: str = Query(...),
    artist: str = Query(...),
    title: str = Query(...),
    language: str = Query(default=""),
):
    # Log the play event
    asyncio.create_task(asyncio.to_thread(record_play, id, title, artist, language))
    
    # Resolve stream URL server-side
    try:
        direct_url = await asyncio.to_thread(jiosaavn_stream, id, title, artist)
        return PlayResponse(stream_url=direct_url)
    except Exception as e:
        print(f"[play] server-side resolution failed for {id}: {e}")
        return PlayResponse()


@app.get("/api/mobile/up_next", response_model=UpNextResponse)
async def up_next(
    song_id: str = Query(...),
    exclude: str = Query(default=""),
    limit: int = Query(default=10),
):
    """
    Radio-style queue allocation — Spotify/Apple Music approach:
    1. Return cached results if fresh (avoids 8+ parallel JioSaavn requests)
    2. Build a large candidate pool from genre, artist, language angles
    3. Interleave candidates round-robin so no source dominates
    4. Exclude already-queued songs passed from the client
    """
    # ── 1. Check up_next cache ───────────────────────────────────────────────
    cached = _upnext_cache.get(song_id)
    if cached and time.time() - cached["ts"] < _UPNEXT_TTL:
        client_exclude: set[str] = set(filter(None, exclude.split(",")))
        songs = [Song(**s) for s in cached["songs"] if s["id"] not in client_exclude]
        if songs:
            return UpNextResponse(songs=songs[:limit])

    # ── 2. Resolve artist / language ────────────────────────────────────────
    history: list = load_json(PLAY_HISTORY_FILE, [])
    history_entry = next((h for h in reversed(history) if h.get("id") == song_id), None)

    artist      = history_entry.get("artist", "") if history_entry else ""
    stored_lang = history_entry.get("language", "") if history_entry else ""

    # Reuse cached details from /play if available — avoids redundant network fetch
    song_data = jiosaavn_song_details(song_id)

    if not artist:
        artist = html.unescape(
            song_data.get("primary_artists", "")
            or song_data.get("more_info", {}).get("primary_artists", "")
        )

    language = song_data.get("language", stored_lang).lower().strip()
    if not language:
        language = detect_language(artist, "", history)

    ra = recent_artists(5)
    queries = build_queue_queries(song_data, artist, language, ra)

    # ── 3. Build exclusion set ───────────────────────────────────────────────
    client_exclude = set(filter(None, exclude.split(",")))
    history_ids    = {h["id"] for h in history[-50:]}
    excluded_ids   = client_exclude | history_ids | {song_id}

    # ── 4. Fetch candidates in parallel with strict language anchoring ─────────
    pool_per_query = max(5, (limit * 2) // max(len(queries), 1))
    fetch_tasks = [
        asyncio.to_thread(jiosaavn_search, q, pool_per_query, 1, language)
        for q in queries
    ]
    batches: list[list[Song]] = await asyncio.gather(*fetch_tasks)

    # ── 5. Round-robin interleave with Normalized Title Deduplication ─────────
    seen_ids: set[str] = set(excluded_ids)
    curr_title = song_data.get("title", "") or (history_entry.get("title", "") if history_entry else "")
    seen_norm_titles: set[str] = {normalize_title(curr_title)} if curr_title else set()
    candidates: list[Song] = []
    max_rounds = max((len(b) for b in batches), default=0)

    for i in range(max_rounds):
        for batch in batches:
            if i < len(batch):
                s = batch[i]
                norm_t = normalize_title(s.title)
                if s.id not in seen_ids and norm_t and norm_t not in seen_norm_titles:
                    seen_ids.add(s.id)
                    seen_norm_titles.add(norm_t)
                    candidates.append(s)

    # ── 6. Cache the full candidate list (before client exclusion) ───────────
    _upnext_cache[song_id] = {
        "ts": time.time(),
        "songs": [s.model_dump() for s in candidates],
    }

    final = [s for s in candidates if s.id not in client_exclude]
    return UpNextResponse(songs=final[:limit])


@app.get("/api/mobile/recommend", response_model=RecommendResponse)
async def recommend(song_id: str = Query(...)):
    history: list = load_json(PLAY_HISTORY_FILE, [])
    history_entry = next((h for h in reversed(history) if h.get("id") == song_id), None)

    artist = history_entry.get("artist", "") if history_entry else ""
    title  = history_entry.get("title", "")  if history_entry else ""
    stored_lang = history_entry.get("language", "") if history_entry else ""

    song_data = jiosaavn_song_details(song_id)
    language = song_data.get("language", stored_lang).lower().strip()
    if not language:
        language = detect_language(artist, title, history)

    if not artist:
        artist = html.unescape(
            song_data.get("primary_artists", "")
            or song_data.get("more_info", {}).get("primary_artists", "")
        )

    primary_first = artist.split(",")[0].strip()
    raw_genre = html.unescape(song_data.get("more_info", {}).get("genres", "") or "")

    queries: list[str] = []

    if primary_first and language:
        queries.append(f"{primary_first} {language} songs")

    for a in recent_artists(3):
        if language:
            queries.append(f"{a} {language} songs")
        else:
            queries.append(f"{a} popular songs")

    if raw_genre and language:
        queries.append(f"{raw_genre} {language}")

    if language:
        queries.append(f"top {language} hits 2025")

    fetch_tasks = [
        asyncio.to_thread(jiosaavn_search, q, 6, 1, language)
        for q in queries
    ]
    batches = await asyncio.gather(*fetch_tasks)

    seen: set[str] = {song_id}
    seen_titles: set[str] = {normalize_title(title)} if title else set()
    recs: list[Song] = []

    for batch in batches:
        for s in batch:
            norm_t = normalize_title(s.title)
            if s.id not in seen and norm_t and norm_t not in seen_titles:
                seen.add(s.id)
                seen_titles.add(norm_t)
                recs.append(s)
            if len(recs) >= 12:
                break
        if len(recs) >= 12:
            break

    return RecommendResponse(recommendations=recs)


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
