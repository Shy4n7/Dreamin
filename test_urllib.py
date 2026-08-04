import urllib.request
import urllib.parse
req = urllib.request.Request("http://example.com/api.php?pids=" + urllib.parse.quote("123&cc=in\r\nHost: 127.0.0.1"))
try:
    urllib.request.urlopen(req)
    print("Success")
except Exception as e:
    print("Error", type(e), e)
