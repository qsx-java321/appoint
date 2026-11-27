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


def summary_optional(summary, name, key):
    metric = summary.get("metrics", {}).get(name)
    if not metric or "values" not in metric:
        return None
    return metric["values"].get(key)


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


def verify_scenario_a(args):
    data = json.loads((PROJECT / args.data).read_text())
    summary = json.loads((PROJECT / args.summary).read_text())
    expected_slots = {(str(s["doctorId"]), s["appointmentDate"], s["sessionType"]) for s in data["slots"]}
    capacity = int(data["capacity"])
    patient_count = len(data["patients"])
    time.sleep(args.settle_seconds)

    check("库存不变量违规数", int(db_scalar("""
        SELECT COUNT(*) FROM schedules
        WHERE total_capacity <> available_capacity + held_capacity + confirmed_capacity
           OR available_capacity < 0 OR held_capacity < 0 OR confirmed_capacity < 0
           OR next_visit_number < 1
           OR held_capacity > total_capacity OR confirmed_capacity > total_capacity
    """)), 0)
    check("场次数量", int(db_scalar("SELECT COUNT(*) FROM schedules")), len(expected_slots))
    check("场次总容量非预期数", int(db_scalar(
        f"SELECT COUNT(*) FROM schedules WHERE total_capacity <> {capacity}"
    )), 0)
    check("统一设置容量", int(db_scalar("SELECT capacity_per_session FROM booking_settings")), capacity)

    db_slots = {(str(row[0]), str(row[1]), str(row[2])) for row in db_rows(
        "SELECT doctor_id, appointment_date, session_type FROM schedules"
    )}
    if db_slots != expected_slots:
        raise AssertionError(
            f"场次集合不一致: missing={sorted(expected_slots - db_slots)}, "
            f"extra={sorted(db_slots - expected_slots)}"
        )
    print("[OK] 场次集合与数据文件一致")

    patients = db_rows("SELECT COUNT(*), COUNT(DISTINCT document_no) FROM patients")[0]
    check("就诊人数量", int(patients[0]), patient_count)
    check("就诊人证件去重数量", int(patients[1]), patient_count)

    recon = db_rows("""
        SELECT s.id
        FROM schedules s
        LEFT JOIN appointment_orders a ON a.schedule_id = s.id
        GROUP BY s.id, s.total_capacity, s.available_capacity,
                 s.held_capacity, s.confirmed_capacity, s.next_visit_number
        HAVING s.confirmed_capacity <> COALESCE(SUM(a.status = 'CONFIRMED'), 0)
            OR s.held_capacity <> COALESCE(SUM(a.status = 'PENDING_PAYMENT'), 0)
            OR s.next_visit_number <> COALESCE(SUM(a.status = 'CONFIRMED'), 0) + 1
            OR s.available_capacity <> s.total_capacity - s.held_capacity - s.confirmed_capacity
    """)
    check("场次与订单对账违规数", len(recon), 0)

    visit_violations = db_rows("""
        SELECT s.id
        FROM schedules s
        JOIN appointment_orders a ON a.schedule_id = s.id AND a.status = 'CONFIRMED'
        GROUP BY s.id
        HAVING COUNT(*) <> COUNT(DISTINCT a.visit_number)
            OR MIN(a.visit_number) <> 1
            OR MAX(a.visit_number) <> COUNT(*)
            OR SUM(a.visit_number IS NULL) > 0
    """)
    check("visit_number 连续性违规数", len(visit_violations), 0)

    claims = int(db_scalar("SELECT COUNT(*) FROM appointment_active_claims"))
    active_orders = int(db_scalar(
        "SELECT COUNT(*) FROM appointment_orders WHERE status IN ('PENDING_PAYMENT','CONFIRMED')"
    ))
    check("有效占用数 = 有效订单数", claims, active_orders)
    check("重复 patient claim 数", int(db_scalar("""
        SELECT COUNT(*) FROM (
            SELECT schedule_id, patient_id FROM appointment_active_claims
            GROUP BY schedule_id, patient_id HAVING COUNT(*) > 1
        ) x
    """)), 0)
    check("重复证件 claim 数", int(db_scalar("""
        SELECT COUNT(*) FROM (
            SELECT schedule_id, document_type, document_no FROM appointment_active_claims
            GROUP BY schedule_id, document_type, document_no HAVING COUNT(*) > 1
        ) x
    """)), 0)

    orders = int(db_scalar("SELECT COUNT(*) FROM appointment_orders"))
    confirmed = int(db_scalar("SELECT COUNT(*) FROM appointment_orders WHERE status = 'CONFIRMED'"))
    pending = int(db_scalar("SELECT COUNT(*) FROM appointment_orders WHERE status = 'PENDING_PAYMENT'"))
    payments = int(db_scalar("SELECT COUNT(*) FROM payment_orders"))
    requests = int(db_scalar("""
        SELECT COUNT(*) FROM appointment_create_requests
        WHERE appointment_order_id IS NOT NULL
          AND response_json IS NOT NULL AND LENGTH(response_json) > 0
    """))
    check("支付单数 = 预约单数", payments, orders)
    check("幂等请求数 = 预约单数", requests, orders)
    check("支付一致性违规数", int(db_scalar("""
        SELECT COUNT(*) FROM appointment_orders a
        JOIN payment_orders p ON p.appointment_order_id = a.id
        WHERE (a.status = 'CONFIRMED' AND p.status <> 'PAID')
           OR (a.status = 'PENDING_PAYMENT' AND p.status <> 'UNPAID')
           OR a.fee_amount <> p.amount OR a.fee_currency <> p.currency
           OR a.user_id <> p.user_id OR a.payment_expires_at <> p.expires_at
    """)), 0)
    check("CANCELLED/EXPIRED 订单数", int(db_scalar(
        "SELECT COUNT(*) FROM appointment_orders WHERE status IN ('CANCELLED','EXPIRED')"
    )), 0)
    check("CLOSED 支付单数", int(db_scalar(
        "SELECT COUNT(*) FROM payment_orders WHERE status = 'CLOSED'"
    )), 0)

    attempts = summary_metric(summary, "booking_attempts", default=0)
    create_ok = summary_metric(summary, "booking_create_ok", default=0)
    pay_ok = summary_metric(summary, "booking_pay_ok", default=0)
    detail_ok = summary_metric(summary, "booking_detail_ok", default=0)
    success = summary_metric(summary, "booking_success", default=0)
    if attempts == 0:
        raise AssertionError("k6 没有执行任何业务迭代")
    if orders == 0:
        raise AssertionError("数据库没有预约单")
    check("DB 预约单数 = k6 create_ok", orders, create_ok)
    check("DB CONFIRMED 数 = k6 pay_ok", confirmed, pay_ok)
    check("k6 success = detail_ok", success, detail_ok)
    if not (create_ok <= attempts and pay_ok <= create_ok and detail_ok <= pay_ok):
        raise AssertionError(
            f"k6 计数顺序异常: attempts={attempts}, create={create_ok}, "
            f"pay={pay_ok}, detail={detail_ok}"
        )
    print(f"[OK] k6 与 DB 对账: attempts={attempts}, create={create_ok}, pay={pay_ok}, "
          f"detail={detail_ok}, orders={orders}, confirmed={confirmed}, pending={pending}")

    schedule_full = summary_metric(summary, "booking_schedule_full", default=0)
    duplicate_claims = summary_metric(summary, "booking_4204", default=0)
    cutoff = summary_metric(summary, "booking_4202", default=0)
    unexpected = summary_metric(summary, "booking_unexpected", default=0)
    if schedule_full or cutoff:
        raise AssertionError(f"出现非预期的 4002/4202: schedule_full={schedule_full}, cutoff={cutoff}")
    if duplicate_claims:
        print(f"[WARN] 4204={duplicate_claims}（重复有效占用被拒绝，库存未超卖）")
    if unexpected > duplicate_claims:
        raise AssertionError(f"存在非 4204 的 unexpected: {unexpected}")
    check("k6 202 卡死数", summary_metric(summary, "booking_202_stuck", default=0), 0)
    dropped = summary_metric(summary, "dropped_iterations", default=0)
    if dropped:
        print(f"[WARN] dropped_iterations={dropped}，该轮目标速率未真正打满，容量数据无效")
    http_failed = summary_metric(summary, "http_req_failed", "rate", default=0)
    if http_failed >= 0.01:
        raise AssertionError(f"http_req_failed 率过高: {http_failed}")
    print(f"[OK] http_req_failed 率: {http_failed}")
    fivexx = summary_metric(summary, "booking_5xx", "rate", default=0)
    if fivexx >= 0.01:
        raise AssertionError(f"booking_5xx 率过高: {fivexx}")
    print(f"[OK] booking_5xx 率: {fivexx}")

    tiers = sorted({
        int(key[len("booking_ok{tier:r"):-1])
        for key in summary.get("metrics", {})
        if key.startswith("booking_ok{tier:r")
    })
    if not tiers:
        raise AssertionError("summary 中没有分档指标")
    tier_rows = []
    highest = None
    for tier in tiers:
        tag = f"r{tier}"
        t_attempts = summary_metric(summary, f"booking_attempts{{tier:{tag}}}", default=0)
        t_success = summary_metric(summary, f"booking_success{{tier:{tag}}}", default=0)
        t_ok = summary_metric(summary, f"booking_ok{{tier:{tag}}}", "rate", default=0)
        t_p95 = summary_optional(summary, f"http_req_duration{{tier:{tag}}}", "p(95)")
        t_p99 = summary_optional(summary, f"http_req_duration{{tier:{tag}}}", "p(99)")
        t_5xx = summary_metric(summary, f"booking_5xx{{tier:{tag}}}", "rate", default=0)
        expected_attempts = int(tag[1:]) * 30
        delivered_ratio = t_attempts / expected_attempts if expected_attempts else 0
        valid_tier = delivered_ratio >= 0.99
        passed = (
            valid_tier
            and t_ok >= 0.99
            and t_p95 is not None and t_p95 < 300
            and t_p99 is not None and t_p99 < 800
            and t_5xx < 0.01
        )
        if passed:
            highest = tier if highest is None else max(highest, tier)
        tier_rows.append({
            "tier": tag,
            "expected_attempts": expected_attempts,
            "attempts": t_attempts,
            "delivered_ratio": delivered_ratio,
            "valid": valid_tier,
            "success": t_success,
            "ok_rate": t_ok,
            "p95": t_p95,
            "p99": t_p99,
            "fivexx": t_5xx,
            "passed": passed,
        })
        print(f"[{'PASS' if passed else 'FAIL'}] {tag}: delivered={t_attempts}/{expected_attempts} "
              f"({delivered_ratio:.2%}), success={t_success}, ok={t_ok:.4f}, "
              f"p95={t_p95}, p99={t_p99}, 5xx={t_5xx}")

    outbox = db_rows("SELECT status, COUNT(*) FROM notification_outbox_events GROUP BY status")
    notifications = int(db_scalar("SELECT COUNT(*) FROM user_notifications"))
    print(f"[INFO] 通知 Outbox: {outbox}；user_notifications={notifications}")
    print(f"[OK] 最高通过档位: {highest if highest is not None else '无'}")

    result = {
        "scenario": "a",
        "orders": orders,
        "confirmed": confirmed,
        "pending": pending,
        "payments": payments,
        "create_requests": requests,
        "claims": claims,
        "patients": patient_count,
        "schedules": len(expected_slots),
        "capacity": capacity,
        "attempts": attempts,
        "success": success,
        "dropped_iterations": dropped,
        "schedule_full": schedule_full,
        "duplicate_claims": duplicate_claims,
        "cutoff": cutoff,
        "unexpected": unexpected,
        "highest_passing_tier": highest,
        "tiers": tier_rows,
        "outbox": outbox,
        "notifications": notifications,
    }
    out = PROJECT / "loadtest/results/verify-a.json"
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(f"[OK] 验证结果: {out.relative_to(PROJECT)}")
    print("PASS: 场景 A 数据一致性验证通过")
    if dropped:
        print(f"[WARN] dropped_iterations={dropped}；未打满的档位已在分档表中标记为 delivered<99%")


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
        verify_scenario_a(args)
    else:
        verify_scenario_b(args)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"[ERROR] {error}", file=sys.stderr)
        sys.exit(1)
