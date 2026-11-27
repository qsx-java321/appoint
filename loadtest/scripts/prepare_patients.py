#!/usr/bin/env python3
"""批量创建就诊人并写出场景 B/A 的 k6 数据文件。"""
import argparse
import json
import sys

from loadtest_common import (
    PROJECT,
    assert_schedules_empty,
    call,
    create_patients,
    future_bookable_slots,
    load_env_file,
    login,
)


def choose_one_slot(slots):
    morning = [slot for slot in slots if slot["sessionType"] == "MORNING_FIRST"]
    candidates = morning or slots
    latest_date = max(slot["appointmentDate"] for slot in candidates)
    return next(slot for slot in candidates if slot["appointmentDate"] == latest_date)


def main():
    parser = argparse.ArgumentParser(description="准备场景 B/A 的就诊人与场次数据")
    parser.add_argument("--scenario", choices=["b", "a"], required=True)
    parser.add_argument("--patients", type=int, default=None)
    parser.add_argument("--capacity", type=int, default=None)
    parser.add_argument("--min-slots", type=int, default=None)
    parser.add_argument("--include-today", action="store_true")
    parser.add_argument("--env-file", default=".env.test")
    parser.add_argument("--email", default="test-admin@example.local")
    parser.add_argument("--out", default=None)
    args = parser.parse_args()

    defaults = {
        "b": {"patients": 100, "capacity": 1, "min_slots": 1, "out": "loadtest/data/scenario-b.json"},
        "a": {"patients": 300, "capacity": 1000, "min_slots": 144, "out": "loadtest/data/scenario-a.json"},
    }[args.scenario]
    patients_count = args.patients or defaults["patients"]
    capacity = args.capacity or defaults["capacity"]
    min_slots = args.min_slots or defaults["min_slots"]
    out_path = PROJECT / (args.out or defaults["out"])

    env = load_env_file(args.env_file)
    password = env.get("LOCAL_SEED_PASSWORD")
    if not password:
        raise SystemExit(f"{args.env_file} 中缺少 LOCAL_SEED_PASSWORD")
    base_url = env.get("BASE_URL", "http://127.0.0.1:8080/api/v1")

    token = login(base_url, args.email, password)
    assert_schedules_empty()

    status, payload = call(base_url, "GET", "/admin/booking-settings", token=token)
    if status != 200 or payload.get("code") != 0:
        raise SystemExit(f"查询容量失败: HTTP {status}, body={payload}")
    current_capacity = payload["data"]["capacityPerSession"]
    if current_capacity != capacity:
        raise SystemExit(f"当前容量是 {current_capacity}，请先运行 set_capacity.py --capacity {capacity}")

    print(f"[OK] 开始创建 {patients_count} 个就诊人")
    patient_ids = create_patients(base_url, token, patients_count)
    slots = future_bookable_slots(base_url, token, include_today=args.include_today)
    if len(slots) < min_slots:
        raise SystemExit(f"可预约场次只有 {len(slots)} 个，少于要求的 {min_slots} 个")

    selected_slots = [choose_one_slot(slots)] if args.scenario == "b" else slots
    if args.scenario == "b":
        print(f"[OK] 选定场次: {selected_slots[0]}")
    else:
        print(f"[OK] 场次数量: {len(selected_slots)}")

    output = {
        "email": args.email,
        "capacity": capacity,
        "patients": patient_ids,
        "slots": selected_slots,
    }
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n")
    pair_capacity = len(patient_ids) * len(selected_slots)
    print(f"[OK] 数据文件: {out_path.relative_to(PROJECT)}")
    print(f"[OK] 唯一 pair 上限: {len(patient_ids)} 患者 × {len(selected_slots)} 场次 = {pair_capacity}")
    if args.scenario == "a" and pair_capacity < 30000:
        raise SystemExit(f"pair 上限 {pair_capacity} 小于 30000，无法覆盖 A1/A2 的固定 OFFSET")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"[ERROR] {error}", file=sys.stderr)
        sys.exit(1)
