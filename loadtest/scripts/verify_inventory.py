#!/usr/bin/env python3
"""场景 B/A 的数据库与 API 验证。当前完整实现场景 B；场景 A 在 B 审查通过后补充。"""
import argparse
import json
import sys
import time

from loadtest_common import (
    PROJECT,
    call,
    db_rows,
    db_scalar,
    load_env_file,
    login,
)


def as_int(value):
    return int(value) if value is not None else None


def check(label, actual, expected):
    if actual != expected:
        raise AssertionError(f"{label} 不符: 期望 {expected}, 实际 {actual}")
    print(f"[OK] {label}: {actual}")


def schedule_row(doctor_id, appointment_date, session_type):
    rows = db_rows(
        "SELECT id,total_capacity,available_capacity,held_capacity,confirmed_capacity,next_visit_number "
        f"FROM schedules WHERE doctor_id={int(doctor_id)} AND appointment_date='{appointment_date}' "
        f"AND session_type='{session_type}'"
    )
    if len(rows) != 1:
        raise AssertionError(f"场次行数不是 1: {rows}")
    return [as_int(x) for x in rows[0]]


def summary_metric(summary, name, key="count", default=None):
    metric = summary.get("metrics", {}).get(name)
    if not metric or "values" not in metric:
        if default is not None:
            return default
        raise AssertionError(f"summary 缺少指标 {name}")
    value = metric["values"].get(key)
    if value is None:
        if default is not None:
            return default
        raise AssertionError(f"summary 指标 {name} 缺少 {key}")
    return value


def verify_scenario_b(args):
    data = json.loads((PROJECT / args.data).read_text())
    slot = data["slots"][0]
    patient_count = len(data["patients"])
    time.sleep(args.settle_seconds)

    schedule = schedule_row(slot["doctorId"], slot["appointmentDate"], slot["sessionType"])
    check("场次数量", int(db_scalar("SELECT COUNT(*) FROM schedules")), 1)
    check("占号后库存 total,available,held,confirmed,next", schedule[1:], [1, 0, 1, 0, 1])
    schedule_id = schedule[0]

    orders = db_rows(
        f"SELECT id,status,visit_number,payment_expires_at FROM appointment_orders WHERE schedule_id={schedule_id}"
    )
    check("预约单数量", len(orders), 1)
    check("预约状态", orders[0][1], "PENDING_PAYMENT")
    check("预约就诊序号", orders[0][2], None)
    order_id = as_int(orders[0][0])

    payments = db_rows(
        "SELECT p.status,p.amount,p.currency,p.expires_at,a.payment_expires_at "
        "FROM payment_orders p JOIN appointment_orders a ON p.appointment_order_id=a.id "
        f"WHERE a.schedule_id={schedule_id}"
    )
    check("支付单数量", len(payments), 1)
    check("支付状态", payments[0][0], "UNPAID")
    check("支付过期时间与预约一致", payments[0][3], payments[0][4])

    check("有效占用数量", int(db_scalar(
        f"SELECT COUNT(*) FROM appointment_active_claims WHERE schedule_id={schedule_id}"
    )), 1)
    check("幂等请求数量", int(db_scalar(
        "SELECT COUNT(*) FROM appointment_create_requests r "
        "JOIN appointment_orders a ON r.appointment_order_id=a.id "
        f"WHERE a.schedule_id={schedule_id}"
    )), 1)
    check("幂等请求回执完整", int(db_scalar(
        "SELECT COUNT(*) FROM appointment_create_requests r "
        "JOIN appointment_orders a ON r.appointment_order_id=a.id "
        f"WHERE a.schedule_id={schedule_id} AND r.appointment_order_id IS NOT NULL "
        "AND r.response_json IS NOT NULL AND LENGTH(r.response_json)>0"
    )), 1)

    patients = db_rows("SELECT COUNT(*),COUNT(DISTINCT document_no) FROM patients")
    check("就诊人数量", as_int(patients[0][0]), patient_count)
    check("就诊人证件去重数量", as_int(patients[0][1]), patient_count)

    summary = json.loads((PROJECT / args.summary).read_text())
    check("k6 尝试数", summary_metric(summary, "booking_attempts"), 100)
    check("k6 成功数", summary_metric(summary, "booking_success"), 1)
    check("k6 4002 数", summary_metric(summary, "booking_schedule_full"), 99)
    check("k6 unexpected 数", summary_metric(summary, "booking_unexpected"), 0)
    check("k6 4204 数", summary_metric(summary, "booking_4204", default=0), 0)
    check("k6 4202 数", summary_metric(summary, "booking_4202", default=0), 0)
    check("k6 202 卡死数", summary_metric(summary, "booking_202_stuck", default=0), 0)
    check("k6 202 重试数", summary_metric(summary, "booking_retry_202", default=0), 0)
    check("k6 5001 重试数", summary_metric(summary, "booking_retry_5001", default=0), 0)
    outcome_rate = summary_metric(summary, "booking_outcome_ok", "rate")
    if outcome_rate < 0.99:
        raise AssertionError(f"预期结果率低于 99%: {outcome_rate}")
    print(f"[OK] k6 预期结果率: {outcome_rate}")
    print("[OK] 创建后、支付前的数据库与 k6 对账通过")

    if not args.pay_winner:
        print("PASS: 场景 B 创建阶段验证通过")
        return

    env = load_env_file(args.env_file)
    password = env.get("LOCAL_SEED_PASSWORD")
    base_url = env.get("BASE_URL", "http://127.0.0.1:8080/api/v1")
    token = login(base_url, args.email, password)

    status, payload = call(base_url, "POST", f"/appointments/{order_id}/pay", token=token)
    if status != 200 or payload.get("code") != 0:
        raise AssertionError(f"支付胜者失败: HTTP {status}, body={payload}")
    paid = payload["data"]
    check("支付后预约状态", paid["status"], "CONFIRMED")
    check("支付后支付状态", paid["paymentStatus"], "PAID")
    check("支付后就诊序号", paid["visitNumber"], 1)
    first_paid_at = paid["paidAt"]

    schedule_after = schedule_row(slot["doctorId"], slot["appointmentDate"], slot["sessionType"])
    check("支付后库存 total,available,held,confirmed,next", schedule_after[1:], [1, 0, 0, 1, 2])
    order_after = db_rows(f"SELECT status,visit_number FROM appointment_orders WHERE id={order_id}")[0]
    check("DB 预约状态", order_after[0], "CONFIRMED")
    check("DB 就诊序号", as_int(order_after[1]), 1)
    payment_after = db_rows(
        f"SELECT status,paid_at FROM payment_orders WHERE appointment_order_id={order_id}"
    )[0]
    check("DB 支付状态", payment_after[0], "PAID")
    if payment_after[1] is None:
        raise AssertionError("paid_at 为空")
    check("支付后有效占用数量", int(db_scalar(
        f"SELECT COUNT(*) FROM appointment_active_claims WHERE schedule_id={schedule_id}"
    )), 1)

    status, payload = call(base_url, "GET", f"/appointments/{order_id}", token=token)
    if status != 200 or payload.get("code") != 0:
        raise AssertionError(f"查询预约详情失败: HTTP {status}, body={payload}")
    detail = payload["data"]
    check("详情预约状态", detail["status"], "CONFIRMED")
    check("详情支付状态", detail["paymentStatus"], "PAID")
    check("详情就诊序号", detail["visitNumber"], 1)
    if detail.get("visit") is None:
        raise AssertionError("CONFIRMED 预约详情 visit 为空")
    check("详情 canPay", detail["canPay"], False)
    check("详情 canCancel", detail["canCancel"], False)
    check("详情 paymentExpired", detail["paymentExpired"], False)

    status, payload = call(base_url, "POST", f"/appointments/{order_id}/pay", token=token)
    if status != 200 or payload.get("code") != 0:
        raise AssertionError(f"重复支付失败: HTTP {status}, body={payload}")
    replay = payload["data"]
    check("重复支付就诊序号", replay["visitNumber"], 1)
    check("重复支付 paidAt 不变", replay["paidAt"], first_paid_at)

    status, payload = call(base_url, "POST", f"/appointments/{order_id}/cancel", token=token)
    if status != 409 or payload.get("code") != 4003:
        raise AssertionError(f"已确认预约取消预期 409/4003, 实际 HTTP {status}, body={payload}")
    print("[OK] 已确认预约取消返回 409/4003")
    schedule_final = schedule_row(slot["doctorId"], slot["appointmentDate"], slot["sessionType"])
    check("取消失败后库存不变", schedule_final[1:], [1, 0, 0, 1, 2])

    result = {
        "scenario": "b",
        "schedule": schedule_final,
        "order_id": order_id,
        "paid_at": first_paid_at,
        "visit_number": 1,
        "summary": {
            "attempts": 100,
            "success": 1,
            "schedule_full": 99,
            "unexpected": 0,
        },
    }
    out = PROJECT / "loadtest/results/verify-b.json"
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(f"[OK] 验证结果: {out.relative_to(PROJECT)}")
    print("PASS: 场景 B 创建、支付、幂等和取消验证全部通过")


def main():
    parser = argparse.ArgumentParser(description="验证场景 B/A 的库存与数据一致性")
    parser.add_argument("--scenario", choices=["b", "a"], required=True)
    parser.add_argument("--data", required=True)
    parser.add_argument("--summary", required=True)
    parser.add_argument("--pay-winner", action="store_true")
    parser.add_argument("--settle-seconds", type=float, default=2.0)
    parser.add_argument("--env-file", default=".env.test")
    parser.add_argument("--email", default="test-admin@example.local")
    args = parser.parse_args()

    if args.scenario == "a":
        raise SystemExit("场景 A 的验证将在场景 B 审查通过后实现")
    verify_scenario_b(args)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"[ERROR] {error}", file=sys.stderr)
        sys.exit(1)
