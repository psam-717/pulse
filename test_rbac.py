import urllib.request
import urllib.error
import json

BASE = "http://localhost:8080"

def req(method, path, body=None, token=None):
    url = f"{BASE}{path}"
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body else None
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        resp = urllib.request.urlopen(r)
        return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())

ok = True

# 1. Super Admin Login
status, resp = req("POST", "/api/hospitals/login",
    {"email": "superadmin@pulse.gh", "password": "superadmin123"})
sa_token = resp.get("token", "")
ok &= status == 200
print(f"{'✅' if status==200 else '❌'} SA login: {status} role={resp.get('role')}")

# 2. Hospital Admin Login
status, resp = req("POST", "/api/hospitals/login",
    {"email": "admin@testhospital.gh", "password": "admin123"})
ha_token = resp.get("token", "")
ok &= status == 200
print(f"{'✅' if status==200 else '❌'} HA login: {status} role={resp.get('role')}")

# 3. SA creates department (validation fail = passed auth gate)
status, resp = req("POST", "/api/hospitals/1/departments",
    {"name":"TestDept","description":"test","specialty":"General"},
    token=sa_token)
ok &= status == 400  # validation error, not 403
print(f"{'✅' if status==400 else '❌'} SA creates dept: {status} (expect 400 validation)")

# 4. HA tries to verify license (should 403)
status, resp = req("PUT", "/api/admin/hospitals/3/verify",
    {"status":"APPROVED","notes":"nope"},
    token=ha_token)
ok &= status == 403
print(f"{'✅' if status==403 else '❌'} HA verify license: {status} (expect 403)")

# 5. SA verifies license (should approve)
status, resp = req("PUT", "/api/admin/hospitals/3/verify",
    {"status":"APPROVED","notes":"Verified by super admin"},
    token=sa_token)
ok &= status == 200
print(f"{'✅' if status==200 else '❌'} SA verify license: {status} body={json.dumps(resp, indent=2)[:200]}")

print(f"\n{'🎉 ALL TESTS PASSED!' if ok else '❌ SOME TESTS FAILED'}")