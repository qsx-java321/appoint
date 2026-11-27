#!/usr/bin/env python3
"""在 schedules 为空时设置统一场次容量。"""
import argparse
import sys

from loadtest_common import (
    PROJECT,
    assert_schedules_empty,
    load_env_file,
    login,
    set_capacity,
    success,
    call,
)


def main():
    parser = argparse.ArgumentParser(description="设置 test 环境统一场次容量")
    parser.add_argument("--capacity", type=int, required=True, help="每场容量")
    parser.add_argument("--fee", default="10.00", help="挂号费，默认 10.00")
    parser.add_argument("--env-file", default=".env.test")
    parser.add_argument("--email", default="test-admin@example.local")
    args = parser.parse_args()

    if args.capacity < 1:
        raise SystemExit("capacity 必须 >= 1")
    env = load_env_file(args.env_file)
    password = env.get("LOCAL_SEED_PASSWORD")
    if not password:
        raise SystemExit(f"{args.env_file} 中缺少 LOCAL_SEED_PASSWORD")
    base_url = env.get("BASE_URL", "http://127.0.0.1:8080/api/v1")

    token = login(base_url, args.email, password)
    assert_schedules_empty()
    data = set_capacity(base_url, token, args.capacity, args.fee)
    print(f"[OK] 容量已设置为 {data['capacityPerSession']}，费用 {data['registrationFee']}，币种 {data['currency']}")

    status, payload = call(base_url, "GET", "/admin/booking-settings", token=token)
    if status != 200 or payload.get("code") != 0:
        raise SystemExit(f"回读容量失败: HTTP {status}, body={payload}")
    print(f"[OK] 回读容量: {payload['data']['capacityPerSession']}")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"[ERROR] {error}", file=sys.stderr)
        sys.exit(1)
