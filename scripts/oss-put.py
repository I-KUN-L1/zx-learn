"""OSS 签名上传（PutObject，对象级 public-read）。

用法：python oss-put.py <accessKeyId> <accessKeySecret> <endpointHost> <bucket> <objectKey> <filePath>
签名算法：OSS Signature V1（HMAC-SHA1）。
"""
import base64
import hashlib
import hmac
import sys
import urllib.request
from email.utils import formatdate

ak, sk, host, bucket, key, path = sys.argv[1:7]

content_type = "image/svg+xml"
acl = "public-read"
date = formatdate(usegmt=True)
string_to_sign = "\n".join([
    "PUT", "", content_type, date, f"x-oss-object-acl:{acl}", f"/{bucket}/{key}",
])
signature = base64.b64encode(
    hmac.new(sk.encode(), string_to_sign.encode(), hashlib.sha1).digest()
).decode()

url = f"https://{host}/{key}"
req = urllib.request.Request(url, method="PUT")
req.add_header("Date", date)
req.add_header("Content-Type", content_type)
req.add_header("x-oss-object-acl", acl)
req.add_header("Authorization", f"OSS {ak}:{signature}")
with open(path, "rb") as f:
    body = f.read()
req.add_header("Content-Length", str(len(body)))

try:
    with urllib.request.urlopen(req, data=body, timeout=60) as resp:
        print(f"OK {key} status={resp.status}")
except Exception as e:  # noqa: BLE001
    detail = ""
    if hasattr(e, "read"):
        detail = e.read().decode("utf-8", "replace")[:500]
    print(f"FAIL {key} err={e} body={detail}")
    sys.exit(1)
