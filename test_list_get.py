import json

cache = [{"name": "test"}]
try:
    cache.get("songs", [])
except Exception as e:
    print("Error:", type(e), e)
