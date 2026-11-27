#!/usr/bin/env python3
"""为场景 C 准备一份可读数据：登录、创建就诊人、创建并支付预约，写出 k6 数据文件。"""
import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]


def load_env_file(path):
    values = {}
    if not path.exists():
        return values
    for raw in path.read_text().splitlines():
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


def ensure_patient(base_url, token):
    status, payload = call(base_url, "POST", "/patients", token=token, body={
        "name": "压测读基线就诊人",
        "documentType": "RESIDENT_ID_CARD",
        "documentNo": "11010519491231002X",
        "contactPhone": "13800138000",
        "contactEmail": "read-baseline@example.com",
    })
    if status == 201 and payload.get("code") == 0:
        patient = payload["data"]
        print(f"[OK] 创建就诊人: id={patient['id']}")
        return patient
    if status == 409 and payload.get("code") == 4101:
        patients = success(base_url, "GET", "/patients", "查询已有就诊人", token=token)
        if not patients:
            raise AssertionError(f"就诊人已存在但列表为空: {payload}")
        print(f"[OK] 复用已有就诊人: id={patients[0]['id']}")
        return patients[0]
    raise AssertionError(f"创建就诊人失败: HTTP {status}, body={payload}")


def pay(base_url, token, appointment_id):
    paid = success(base_url, "POST", f"/appointments/{appointment_id}/pay", "支付预约", token=token)
    if paid.get("status") != "CONFIRMED":
        raise AssertionError(f"支付后状态不是 CONFIRMED: {paid}")
    return paid


def resolve_appointment(base_url, token, patient_id, slot):
    booking_token = success(base_url, "POST", "/appointments/token", "领取预约令牌", token=token)["token"]
    status, payload = call(base_url, "POST", "/appointments", token=token, headers={
        "Idempotency-Key": booking_token,
    }, body={
        "doctorId": slot["doctorId"],
        "appointmentDate": slot["appointmentDate"],
        "sessionType": slot["sessionType"],
        "patientId": patient_id,
    })
    if status == 201 and payload.get("code") == 0:
        appointment = payload["data"]
        pay(base_url, token, appointment["appointmentOrderId"])
        print(f"[OK] 创建并支付预约: id={appointment['appointmentOrderId']}")
        return appointment["appointmentOrderId"], slot["appointmentDate"], slot["sessionType"]

    mine = success(base_url, "GET", "/appointments/my", "查询已有预约", token=token) or []
    existing = next((x for x in mine if x.get("status") == "CONFIRMED"), None)
    if existing is None:
        existing = next((x for x in mine if x.get("status") == "PENDING_PAYMENT"), None)
    if existing is None:
        raise AssertionError(f"创建预约失败且没有可复用预约: HTTP {status}, body={payload}")
    if existing["status"] == "PENDING_PAYMENT":
        pay(base_url, token, existing["id"])
    print(f"[OK] 复用已有预约: id={existing['id']}, status={existing['status']}")
    return existing["id"], existing["appointmentDate"], existing["sessionType"]


def poll_notification(base_url, token, timeout=15):
    deadline = time.time() + timeout
    while time.time() < deadline:
        items = success(base_url, "GET", "/notifications", "查询通知", token=token) or []
        if items:
            print(f"[OK] 通知已到达: {len(items)} 条")
            return
        time.sleep(1)
    print("[WARN] 15 秒内没有收到通知；读基线仍可继续，通知接口会返回空列表")


def main():
    parser = argparse.ArgumentParser(description="准备场景 C 读基线数据")
    parser.add_argument("--env-file", default=".env.test", help="环境变量文件，默认 .env.test")
    parser.add_argument("--base-url", default=None, help="后端 API 地址")
    parser.add_argument("--email", default=None, help="登录邮箱，默认 test-admin@example.local")
    parser.add_argument("--out", default="loadtest/data/scenario-c.json", help="输出的数据文件")
    args = parser.parse_args()

    env = load_env_file(PROJECT / args.env_file)
    base_url = args.base_url or env.get("BASE_URL") or "http://127.0.0.1:8080/api/v1"
    email = args.email or env.get("LOAD_EMAIL") or "test-admin@example.local"
    password = env.get("LOCAL_SEED_PASSWORD")
    if not password:
        raise SystemExit(f"{args.env_file} 中缺少 LOCAL_SEED_PASSWORD")

    login_data = success(base_url, "POST", "/auth/login", "登录", body={
        "email": email,
        "password": password,
    })
    token = login_data["accessToken"]
    print(f"[OK] 登录: {email}")

    hospitals = success(base_url, "GET", "/hospitals", "查询医院", token=token)
    hospital = hospitals[0]
    departments = success(base_url, "GET", f"/hospitals/{hospital['id']}/departments", "查询科室", token=token)
    department = departments[0]
    doctors = success(base_url, "GET", f"/departments/{department['id']}/doctors", "查询医生", token=token)
    doctor = doctors[0]
    schedules = success(base_url, "GET", f"/doctors/{doctor['id']}/schedules", "查询场次", token=token)
    slot = next((x for x in schedules if x.get("bookable")), None)
    if slot is None:
        raise AssertionError("没有可预约场次，无法准备预约详情数据")
    print(f"[OK] 选中场次: doctor={doctor['id']} {slot['appointmentDate']} {slot['sessionType']}")

    patient = ensure_patient(base_url, token)
    appointment_id, appointment_date, session_type = resolve_appointment(base_url, token, patient["id"], slot)
    poll_notification(base_url, token)

    output = {
        "email": email,
        "hospitalId": hospital["id"],
        "departmentId": department["id"],
        "doctorId": doctor["id"],
        "appointmentId": appointment_id,
        "appointmentDate": appointment_date,
        "sessionType": session_type,
        "patientId": patient["id"],
    }
    out_path = PROJECT / args.out
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n")
    print(f"[OK] 数据文件已写入: {out_path.relative_to(PROJECT)}")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"[ERROR] {error}", file=sys.stderr)
        sys.exit(1)
