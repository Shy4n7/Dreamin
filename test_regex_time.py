import re
import time

def clean_title(raw: str) -> str:
    title = raw
    title = re.sub(r'\s*\(?\s*[Ff]rom\s+["\u201c\u2018].*?["\u201d\u2019]?\s*\)?$', '', title).strip()
    return title

t0 = time.time()
clean_title("From \"" + "a " * 50000 + " ")
print(time.time() - t0)
