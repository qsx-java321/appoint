"""压测脚本共用：环境、HTTP、目录/场次、身份证生成和 MySQL 查询。"""
import json
import re
import subprocess
import urllib.error
import urllib.request
from datetime import date, datetime
from pathlib import Path
from zoneinfo import ZoneInfo

PROJECT = Path(__file__).resolve().parents[2]

ID_WEIGHTS = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
ID_CHECK_CODES = ["1", "0", "X", "9", "8", "7", "6", "5", "4", "3", "2"]


def load_env_file(path=".env.test"):
    env_path = Path(path)
    if not env_path.is_absolute():
        env_path = PROJECT / env_path
    values = {}
    if not env_path.exists():
        return values
    for raw in env_path.read_text().splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key] = value.strip().strip('"').strip("'")
    return values


def call(base_url, method, path, token=None, body=None, headers=None):
    data = None
    request_headers = {}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode()
        request_headers["Content-Type"] = "application/json"
    if token:
        request_headers["Authorization"] = "Bearer " + token
    if headers:
        request_headers.update(headers)
    request = urllib.request.Request(base_url + path, data=data, headers=request_headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read().decode()
            return response.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as error:
        raw = error.read().decode()
        try:
            payload = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            payload = {"raw": raw}
        return error.code, payload


def success(base_url, method, path, label, token=None, body=None, expected=(200, 201)):
    status, payload = call(base_url, method, path, token=token, body=body)
    if status not in expected or payload.get("code") != 0:
        raise AssertionError(f"{label} 失败: HTTP {status}, body={payload}")
    return payload.get("data")


def login(base_url, email, password):
    data = success(base_url, "POST", "/auth/login", "登录", body={"email": email, "password": password})
    return data["accessToken"]


def get_all_doctors(base_url, token):
    hospitals = success(base_url, "GET", "/hospitals", "查询医院", token=token)
    doctors = {}
    for hospital in hospitals:
        departments = success(base_url, "GET", f"/hospitals/{hospital['id']}/departments", "查询科室", token=token)
        for department in departments:
            items = success(base_url, "GET", f"/departments/{department['id']}/doctors", "查询医生", token=token)
            for doctor in items:
                doctors[str(doctor["id"])] = doctor
    return list(doctors.values())


def get_schedules(base_url, token, doctor_id):
    return success(base_url, "GET", f"/doctors/{doctor_id}/schedules", "查询场次", token=token)


def business_today_from(schedules):
    for item in schedules or []:
        server_time = item.get("serverTime")
        if server_time:
            return date.fromisoformat(server_time[:10])
    return datetime.now(ZoneInfo("Asia/Shanghai")).date()


def future_bookable_slots(base_url, token, include_today=False):
    doctors = get_all_doctors(base_url, token)
    slots = {}
    today = None
    for doctor in doctors:
        schedules = get_schedules(base_url, token, doctor["id"])
        if today is None:
            today = business_today_from(schedules)
        for item in schedules:
            if not item.get("bookable"):
                continue
            appointment_date = item["appointmentDate"]
            if not include_today and date.fromisoformat(appointment_date) <= today:
                continue
            key = (str(doctor["id"]), appointment_date, item["sessionType"])
            slots[key] = {
                "doctorId": str(doctor["id"]),
                "appointmentDate": appointment_date,
                "sessionType": item["sessionType"],
                "startTime": item["startTime"],
            }
    return sorted(slots.values(), key=lambda x: (x["appointmentDate"], x["startTime"], int(x["doctorId"])))


def compute_id_check(base17):
    total = sum(int(ch) * weight for ch, weight in zip(base17, ID_WEIGHTS))
    return ID_CHECK_CODES[total % 11]


def validate_id_card(id_no):
    if not re.fullmatch(r"\d{17}[0-9X]", id_no):
        raise ValueError(f"身份证格式非法: {id_no}")
    if id_no.startswith("000000"):
        raise ValueError(f"身份证地区码非法: {id_no}")
    if id_no[14:17] == "000":
        raise ValueError(f"身份证顺序码非法: {id_no}")
    try:
        datetime.strptime(id_no[6:14], "%Y%m%d")
    except ValueError as error:
        raise ValueError(f"身份证出生日期非法: {id_no}") from error
    expected = compute_id_check(id_no[:17])
    if id_no[-1] != expected:
        raise ValueError(f"身份证校验位错误: {id_no}, 期望 {expected}")
    return id_no


def generate_id_card(index, area="110105", birth="19800101"):
    if not 1 <= index <= 999:
        raise ValueError("顺序码只支持 001..999")
    base = f"{area}{birth}{index:03d}"
    id_no = base + compute_id_check(base)
    return validate_id_card(id_no)


def create_patients(base_url, token, count):
    patient_ids = []
    for index in range(1, count + 1):
        document_no = generate_id_card(index)
        status, payload = call(base_url, "POST", "/patients", token=token, body={
            "name": f"压测患者{index:03d}",
            "documentType": "RESIDENT_ID_CARD",
            "documentNo": document_no,
            "contactPhone": f"138{index:08d}",
            "contactEmail": f"loadtest-{index:03d}@example.com",
        })
        if status != 201 or payload.get("code") != 0:
            raise AssertionError(f"创建第 {index} 个就诊人失败: HTTP {status}, body={payload}")
        patient_ids.append(payload["data"]["id"])
        if index % 50 == 0:
            print(f"[OK] 已创建 {index}/{count} 个就诊人")
    return patient_ids


def set_capacity(base_url, token, capacity, fee="10.00"):
    data = success(base_url, "PUT", "/admin/booking-settings", "更新容量", token=token, body={
        "capacityPerSession": capacity,
        "registrationFee": fee,
    })
    if data.get("capacityPerSession") != capacity:
        raise AssertionError(f"容量未生效: {data}")
    return data


def db_rows(sql):
    env = load_env_file(".env.test")
    if not env.get("MYSQL_USER") or not env.get("MYSQL_PASSWORD"):
        raise AssertionError(".env.test 缺少 MYSQL_USER / MYSQL_PASSWORD")
    cmd = [
        "docker", "compose", "--env-file", "compose.env", "exec", "-T",
        "-e", f"MYSQL_PWD={env['MYSQL_PASSWORD']}",
        "mysql", "mysql", "-u" + env["MYSQL_USER"], "appoint_test", "-N", "-B", "-e", sql,
    ]
    result = subprocess.run(cmd, cwd=PROJECT, capture_output=True, text=True)
    if result.returncode != 0:
        raise AssertionError(f"数据库查询失败: {result.stderr.strip()}")
    rows = []
    for line in result.stdout.strip().splitlines():
        rows.append([None if value == "NULL" else value for value in line.split("\t")])
    return rows


def db_scalar(sql):
    rows = db_rows(sql)
    return rows[0][0] if rows else None


def schedules_count():
    return int(db_scalar("SELECT COUNT(*) FROM schedules") or 0)


def assert_schedules_empty():
    count = schedules_count()
    if count != 0:
        raise AssertionError(f"schedules 已有 {count} 行；必须先重置 test，再设置容量并准备数据")
