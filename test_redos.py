import re
import time
s = ' From "' + 'a' * 100000
t0 = time.time()
re.sub(r'\s*\(?\s*[Ff]rom\s+["\u201c\u2018].*?["\u201d\u2019]?\s*\)?$', '', s)
print('Time:', time.time() - t0)
