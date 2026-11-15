"""确认demo启动后的初始资源及预置账号可用；仅脱敏保存登录结果。"""
import json
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import local_env

out = Path(__file__).parent / 'evidence'
seed = local_env.read_properties(ROOT / '.env.demo')
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

def api(method, route, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request('http://127.0.0.1:8082/api/v1' + route,
        data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    with opener.open(request, timeout=15) as response:
        result = json.load(response)
        assert response.status == 200 and result['code'] == 0
        return result['data']

checks = {}
for role in ('user', 'doctor', 'admin'):
    login = api('POST', '/auth/login', {'email': f'demo-{role}@example.local', 'password': seed['LOCAL_SEED_PASSWORD']})
    assert role.upper() in login['user']['roles']
    checks[role] = {'loginStatus': 200, 'roles': login['user']['roles']}
    if role == 'doctor':
        sessions = api('GET', '/doctor/sessions', token=login['accessToken'])
        assert len(sessions) == 4 and all(row['confirmedCount'] == 0 for row in sessions)
        checks[role]['emptySessions'] = 4
    if role == 'admin':
        checks['settings'] = api('GET', '/admin/booking-settings', token=login['accessToken'])
baseline = {
    'counts': local_env.mysql("USE appoint_demo; SELECT 'users',COUNT(*) FROM users UNION ALL SELECT 'patients',COUNT(*) FROM patients UNION ALL SELECT 'appointments',COUNT(*) FROM appointment_orders UNION ALL SELECT 'payments',COUNT(*) FROM payment_orders;"),
    'catalog': local_env.mysql('SET NAMES utf8mb4; USE appoint_demo; SELECT h.id,h.name,d.id,d.name,dr.id,dr.doctor_no,dr.name FROM hospitals h JOIN departments d ON d.hospital_id=h.id JOIN doctors dr ON dr.department_id=d.id ORDER BY h.id,d.id,dr.id;'),
    'seedChecks': checks,
}
assert baseline['counts'] == 'users\t3\npatients\t0\nappointments\t0\npayments\t0'
(out / 'baseline.json').write_text(json.dumps(baseline, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(baseline, ensure_ascii=False, indent=2))
