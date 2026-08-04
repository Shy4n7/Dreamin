import urllib.request
import urllib.parse
def jiosaavn_search(query: str, limit: int = 15, page: int = 1):
    encoded = urllib.parse.quote(query)
    url = (
        f"https://www.jiosaavn.com/api.php"
        f"?__call=search.getResults"
        f"&_format=json&_marker=0&api_version=4&ctx=web6dot0"
        f"&q={encoded}&n={limit}&p={page}"
    )
    print("URL:", url)

jiosaavn_search("123\r\nHost: 127.0.0.1")
