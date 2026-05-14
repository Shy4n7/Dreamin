import urllib.request

def _fetch_song_details_raw(song_id: str) -> dict:
    url = (
        f"https://www.jiosaavn.com/api.php"
        f"?__call=song.getDetails&cc=in&_marker=0&_format=json&pids={song_id}"
    )
    print("Fetching:", url)

_fetch_song_details_raw("123&cc=in\r\nHost: 127.0.0.1")
