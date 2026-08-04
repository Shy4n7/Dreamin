import re
import time
s = ' From "' + ' ' * 100000 + 'a'
t0 = time.time()
print(re.sub(r'\s*\(?\s*[Ff]rom\s+["\u201c\u2018][^"\u201d\u2019]*["\u201d\u2019]?\s*\)?$', '', s))
print('Time:', time.time() - t0)
