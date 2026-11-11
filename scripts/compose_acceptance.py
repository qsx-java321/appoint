#!/usr/bin/env python3
"""在 Compose 依赖上运行隔离的真实 HTTP 验收。"""
import argparse
import datetime
import json
import os
import secrets
import signal
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RUN = uuid.uuid4().hex[:12]
DB = 'accept_' + RUN
USER = 'a_' + RUN
PASSWORD = secrets.token_hex(16)
VHOST = 'accept-' + RUN
LOG = Path(tempfile.gettempdir()) / ('appoint-accept-' + RUN + '.log')
HTTP_CLIENT = 'urllib'
HTTP_REPORT = None
HTTP_RECORDS = []


def redact(value):
    if isinstance(value, dict):
        private = {'accessToken', 'password', 'signature', 'documentNo', 'contactPhone', 'contactEmail', 'email'}
        return {key: '[redacted]' if key in private else redact(item) for key, item in value.items()}
    if isinstance(value, list):
        return [redact(item) for item in value]
    return value


def record_response(method, path, status, expected, result):
    HTTP_RECORDS.append({'method': method, 'path': path, 'status': status, 'expectedStatus': expected,
                         'response': redact(result)})
    if HTTP_REPORT:
        HTTP_REPORT.write_text(json.dumps({'runId': RUN, 'httpClient': HTTP_CLIENT,
                                          'requests': HTTP_RECORDS}, ensure_ascii=False, indent=2) + '\n')
    if status != expected:
        raise RuntimeError(f'{method} {path}: HTTP {status}, expected {expected}')
    return result.get('data')


def curl_request(port, method, path, data, token, headers, expected):
    # 凭据与请求体只经 stdin 传入，避免出现在进程命令行和请求记录中。
    config = ['header = "Content-Type: application/json"']
    if token:
        config.append('header = ' + json.dumps('Authorization: Bearer ' + token))
    for key, value in (headers or {}).items():
        config.append('header = ' + json.dumps(key + ': ' + value))
    if data is not None:
        config.append('data = ' + json.dumps(json.dumps(data, ensure_ascii=False), ensure_ascii=False))
    response = subprocess.run(['curl', '--silent', '--show-error', '--max-time', '10', '--noproxy', '*',
                               '--request', method, '--url', f'http://127.0.0.1:{port}{path}',
                               '--config', '-', '--write-out', '\n%{http_code}'],
                              input='\n'.join(config) + '\n', text=True, capture_output=True, check=True)
    body, status = response.stdout.rsplit('\n', 1)
    return record_response(method, path, int(status), expected, json.loads(body))


def command(*args, input_text=None, capture=False):
    return subprocess.run(args, cwd=ROOT, input=input_text, text=True, check=True,
                          stdout=subprocess.PIPE if capture else subprocess.DEVNULL,
                          stderr=subprocess.PIPE if capture else None).stdout


def mysql(sql):
    return command('docker', 'compose', '--env-file', 'compose.env', 'exec', '-T', 'mysql', 'sh', '-c',
                   'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot -N -B',
                   input_text=sql, capture=True).strip()


def rabbit(*args):
    command('docker', 'compose', '--env-file', 'compose.env', 'exec', '-T', 'rabbitmq', 'rabbitmqctl', *args)


def request(port, method, path, data=None, token=None, headers=None, expected=200):
    if HTTP_CLIENT == 'curl':
        return curl_request(port, method, path, data, token, headers, expected)
    payload = json.dumps(data).encode() if data is not None else None
    values = {'Content-Type': 'application/json'}
    if token:
        values['Authorization'] = 'Bearer ' + token
    values.update(headers or {})
    req = urllib.request.Request(f'http://127.0.0.1:{port}{path}', data=payload,
                                 method=method, headers=values)
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            result = json.load(response)
            return record_response(method, path, response.status, expected, result)
    except urllib.error.HTTPError as exc:
        return record_response(method, path, exc.code, expected, json.load(exc))


def wait_app(app, port):
    deadline = time.monotonic() + 120
    while True:
        if app.poll() is not None:
            raise RuntimeError('应用启动失败')
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{port}/api/v1/hospitals', timeout=2):
                return
        except urllib.error.HTTPError as exc:
            if exc.code == 401:
                return
        except OSError:
            pass
        if time.monotonic() >= deadline:
            raise RuntimeError('应用启动超时')
        time.sleep(1)


def stop_app(app):
    if app.poll() is None:
        os.killpg(app.pid, signal.SIGTERM)
        try:
            app.wait(timeout=15)
        except subprocess.TimeoutExpired:
            os.killpg(app.pid, signal.SIGKILL)
            app.wait()


def wait_confirmed(port, appointment, token):
    deadline = time.monotonic() + 30
    while True:
        detail = request(port, 'GET', f'/api/v1/appointments/{appointment}', token=token)
        if detail['status'] == 'CONFIRMED':
            return detail
        if time.monotonic() >= deadline:
            raise RuntimeError('就诊验收预约确认超时')
        time.sleep(.5)


def sample_document(sequence):
    prefix = '11010519900101' + f'{sequence:03d}'
    weights = (7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
    return prefix + '10X98765432'[sum(int(digit) * weight for digit, weight in zip(prefix, weights)) % 11]


def main():
    command('docker', 'compose', '--env-file', 'compose.env', 'config', '--quiet')
    config = json.loads(command('docker', 'compose', '--env-file', 'compose.env', 'config', '--format', 'json', capture=True))
    rabbit_user = config['services']['rabbitmq']['environment']['RABBITMQ_DEFAULT_USER']
    rabbit_pass = config['services']['rabbitmq']['environment']['RABBITMQ_DEFAULT_PASS']
    command('docker', 'compose', '--env-file', 'compose.env', 'up', '-d', '--wait', 'mysql', 'redis', 'rabbitmq')
    assert command('docker', 'compose', '--env-file', 'compose.env', 'exec', '-T', 'redis', 'redis-cli', 'ping', capture=True).strip() == 'PONG'
    rabbit('status', '--quiet')
    mysql(f"CREATE DATABASE `{DB}`; CREATE USER '{USER}'@'%' IDENTIFIED BY '{PASSWORD}'; GRANT ALL PRIVILEGES ON `{DB}`.* TO '{USER}'@'%';")
    rabbit('add_vhost', VHOST)
    rabbit('set_permissions', '-p', VHOST, rabbit_user, '.*', '.*', '.*')
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    env = os.environ.copy()
    env.update({'DB_URL': f'jdbc:mysql://127.0.0.1:3306/{DB}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai',
                'MYSQL_USER': USER, 'MYSQL_PASSWORD': PASSWORD,
                'SPRING_RABBITMQ_HOST': '127.0.0.1', 'SPRING_RABBITMQ_PORT': '5672',
                'SPRING_RABBITMQ_USERNAME': rabbit_user, 'SPRING_RABBITMQ_PASSWORD': rabbit_pass,
                'SPRING_RABBITMQ_VIRTUAL_HOST': VHOST, 'SPRING_PROFILES_ACTIVE': 'dev',
                'SERVER_PORT': str(port), 'APPOINT_OUTBOX_SCAN_DELAY': '500ms',
                'APPOINT_EXPIRATION_ENABLED': 'false', 'APPOINT_RECONCILIATION_ENABLED': 'false',
                'APPOINT_JWT_SECRET': secrets.token_urlsafe(48)})
    success = False
    with LOG.open('w') as log:
        app = subprocess.Popen(['./mvnw', '-o', 'spring-boot:run'], cwd=ROOT, env=env,
                               stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_app(app, port)
            email = RUN + '@example.com'
            credentials = {'email': email, 'password': 'password123'}
            request(port, 'POST', '/api/v1/auth/register', credentials, expected=201)
            token = request(port, 'POST', '/api/v1/auth/login', credentials)['accessToken']
            # 查看真实目录，验证完整的用户选院、科室、医生入口。
            hospitals = request(port, 'GET', '/api/v1/hospitals', token=token)
            departments = request(port, 'GET', f"/api/v1/hospitals/{hospitals[0]['id']}/departments", token=token)
            doctors = request(port, 'GET', f"/api/v1/departments/{departments[0]['id']}/doctors", token=token)
            assert any(item['id'] == '1' for item in doctors)
            patient = request(port, 'POST', '/api/v1/patients', {
                'name': '验收就诊人', 'documentType': 'RESIDENT_ID_CARD',
                'documentNo': '110105199001010010', 'contactPhone': '13800138000',
                'contactEmail': 'patient@example.com'}, token, expected=201)['id']
            patients = request(port, 'GET', '/api/v1/patients', token=token)
            assert any(item['id'] == patient for item in patients)
            request(port, 'GET', '/api/v1/doctors/1/schedules', token=token)
            date = (datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).date()
                    + datetime.timedelta(days=3)).isoformat()
            order = request(port, 'POST', '/api/v1/appointments', {
                'doctorId': '1', 'appointmentDate': date, 'sessionType': 'MORNING_FIRST',
                'patientId': patient}, token, {'Idempotency-Key': str(uuid.uuid4())}, 201)
            appointment, payment = order['appointmentOrderId'], order['paymentOrderId']
            attempt = request(port, 'POST', f'/api/v1/payments/{payment}/attempts',
                              {'channel': 'MOCK'}, token, expected=202)
            request(port, 'POST', attempt['checkoutUrl'] + '/complete', token=token, expected=202)
            deadline = time.monotonic() + 30
            while True:
                detail = request(port, 'GET', f'/api/v1/appointments/{appointment}', token=token)
                if detail['status'] == 'CONFIRMED':
                    assert detail['visitNumber'] is not None
                    break
                if time.monotonic() >= deadline:
                    raise RuntimeError('预约确认超时')
                time.sleep(.5)
            request(port, 'POST', f'/api/v1/appointments/{appointment}/cancel', token=token, expected=202)
            deadline = time.monotonic() + 30
            while True:
                detail = request(port, 'GET', f'/api/v1/appointments/{appointment}', token=token)
                if detail['paymentStatus'] == 'REFUNDED':
                    break
                if time.monotonic() >= deadline:
                    raise RuntimeError('退款完成超时')
                time.sleep(.5)
            result = mysql(f"USE `{DB}`; SELECT COUNT(*) FROM appointment_orders a JOIN schedules s ON s.id=a.schedule_id JOIN payment_orders p ON p.appointment_order_id=a.id JOIN payment_refunds r ON r.payment_order_id=p.id WHERE a.id={int(appointment)} AND a.status='CANCELLED' AND p.status='REFUNDED' AND s.total_capacity=s.available_capacity+s.held_capacity+s.confirmed_capacity AND NOT EXISTS (SELECT 1 FROM appointment_active_claims c WHERE c.appointment_order_id=a.id) AND EXISTS (SELECT 1 FROM payment_outbox_events o WHERE o.aggregate_id=p.id AND o.event_type='PaymentSucceeded') AND EXISTS (SELECT 1 FROM coordination_inbox_events i WHERE i.event_type='PaymentSucceeded');")
            if result != '1':
                raise RuntimeError('数据库业务事实校验失败')
            # 仅在本次隔离库缩短第二张订单截止，验证扫描关闭时由迟到事件释放暂占。
            late = request(port, 'POST', '/api/v1/appointments', {
                'doctorId': '1', 'appointmentDate': date, 'sessionType': 'MORNING_SECOND',
                'patientId': patient}, token, {'Idempotency-Key': str(uuid.uuid4())}, 201)
            late_appointment, late_payment = late['appointmentOrderId'], late['paymentOrderId']
            late_attempt = request(port, 'POST', f'/api/v1/payments/{late_payment}/attempts',
                                   {'channel': 'MOCK'}, token, expected=202)
            mysql(f"USE `{DB}`; UPDATE appointment_orders SET payment_expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id={int(late_appointment)}; UPDATE payment_orders SET expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id={int(late_payment)};")
            request(port, 'POST', late_attempt['checkoutUrl'] + '/complete', token=token, expected=202)
            deadline = time.monotonic() + 30
            while True:
                detail = request(port, 'GET', f'/api/v1/appointments/{late_appointment}', token=token)
                if detail['status'] == 'EXPIRED' and detail['paymentStatus'] == 'REFUNDED':
                    break
                if time.monotonic() >= deadline:
                    raise RuntimeError('迟到支付关闭及退款超时')
                time.sleep(.5)
            result = mysql(f"USE `{DB}`; SELECT COUNT(*) FROM appointment_orders a JOIN schedules s ON s.id=a.schedule_id JOIN payment_orders p ON p.appointment_order_id=a.id WHERE a.id={int(late_appointment)} AND a.status='EXPIRED' AND p.status='REFUNDED' AND a.visit_number IS NULL AND s.available_capacity=s.total_capacity AND s.held_capacity=0 AND s.confirmed_capacity=0 AND s.next_visit_number=1 AND NOT EXISTS (SELECT 1 FROM appointment_active_claims c WHERE c.appointment_order_id=a.id) AND (SELECT COUNT(*) FROM payment_refunds r WHERE r.payment_order_id=p.id AND r.reason='APPOINTMENT_NOT_CONFIRMABLE')=1 AND (SELECT COUNT(*) FROM payment_outbox_events o WHERE o.aggregate_id=p.id AND o.event_type='RefundRequested')=1 AND EXISTS (SELECT 1 FROM payment_outbox_events o JOIN coordination_inbox_events i ON i.event_id=o.event_id WHERE o.aggregate_id=p.id AND o.event_type='PaymentSucceeded');")
            if result != '1':
                raise RuntimeError('迟到支付库存、Inbox及唯一退款事实校验失败')
            # 新增就诊验收：绑定真实注册账号，所有预约和支付都通过 HTTP 创建。
            user_id = request(port, 'GET', '/api/v1/users/me', token=token)['id']
            mysql(f"USE `{DB}`; INSERT INTO user_roles(user_id,role_id) SELECT {int(user_id)},id FROM roles WHERE code='ADMIN';")
            admin_token = request(port, 'POST', '/api/v1/auth/login', credentials)['accessToken']
            doctor_credentials = {'email': 'doctor-' + email, 'password': 'password123'}
            doctor_user = request(port, 'POST', '/api/v1/auth/register', doctor_credentials, expected=201)
            doctor_user_id = doctor_user['id']
            request(port, 'PUT', '/api/v1/admin/doctors/1/account', {'userId': doctor_user_id}, admin_token)
            doctor_token = request(port, 'POST', '/api/v1/auth/login', doctor_credentials)['accessToken']
            assert 'DOCTOR' in request(port, 'GET', '/api/v1/users/me', token=doctor_token)['roles']
            empty_sessions = request(port, 'GET', '/api/v1/doctor/sessions', token=doctor_token)
            assert len(empty_sessions) == 4 and all(item['confirmedCount'] == 0 for item in empty_sessions)
            request(port, 'GET', '/api/v1/doctor/sessions', token=token, expected=403)
            visits = []
            for index in range(4):
                patient_id = patient if index == 0 else request(port, 'POST', '/api/v1/patients', {
                    'name': f'就诊验收{index}', 'documentType': 'RESIDENT_ID_CARD',
                    'documentNo': sample_document(100 + index), 'contactPhone': '13800138000',
                    'contactEmail': 'visit@example.com'}, token, expected=201)['id']
                created = request(port, 'POST', '/api/v1/appointments', {
                    'doctorId': '1', 'appointmentDate': date, 'sessionType': 'MORNING_FIRST',
                    'patientId': patient_id}, token, {'Idempotency-Key': str(uuid.uuid4())}, 201)
                attempt = request(port, 'POST', f"/api/v1/payments/{created['paymentOrderId']}/attempts",
                                  {'channel': 'MOCK'}, token, expected=202)
                request(port, 'POST', attempt['checkoutUrl'] + '/complete', token=token, expected=202)
                detail = wait_confirmed(port, created['appointmentOrderId'], token)
                assert detail['visit']['status'] == 'NOT_CHECKED_IN' and not detail['visit']['canCheckIn']
                visits.append(detail)
            request(port, 'POST', f"/api/v1/appointments/{visits[0]['id']}/check-in", token=token, expected=409)
            # 重启会清空 MockPay 内存；重启前所有资金和事件工作必须已完成。
            deadline = time.monotonic() + 30
            while mysql(f"USE `{DB}`; SELECT (SELECT COUNT(*) FROM payment_outbox_events WHERE status<>'PUBLISHED') + (SELECT COUNT(*) FROM payment_orders WHERE status='REFUND_PENDING');") != '0':
                if time.monotonic() >= deadline:
                    raise RuntimeError('重启前异步资金工作尚未结束')
                time.sleep(.5)
            ids = ','.join(str(int(item['id'])) for item in visits)
            snapshots_sql = f"USE `{DB}`; SELECT * FROM appointment_orders WHERE id IN ({ids}) ORDER BY id; SELECT * FROM schedules WHERE id={int(visits[0]['scheduleId'])}; SELECT * FROM appointment_active_claims WHERE appointment_order_id IN ({ids}) ORDER BY appointment_order_id; SELECT * FROM payment_orders WHERE appointment_order_id IN ({ids}) ORDER BY appointment_order_id;"
            before = mysql(snapshots_sql)
            stop_app(app)
            # 仅测试 classpath 入口接受受控 Clock；生产启动配置不提供该能力。
            env.update({'SPRING_DATASOURCE_URL': env['DB_URL'], 'SPRING_DATASOURCE_USERNAME': USER,
                        'SPRING_DATASOURCE_PASSWORD': PASSWORD,
                        'SPRING_DATASOURCE_DRIVER_CLASS_NAME': 'com.mysql.cj.jdbc.Driver',
                        'APPOINT_ACCEPTANCE_INSTANT': date + 'T00:00:00Z',
                        'APPOINT_MESSAGING_ENABLED': 'false', 'APPOINT_OUTBOX_ENABLED': 'false'})
            app = subprocess.Popen(['./mvnw', '-o', 'spring-boot:test-run',
                                    '-Dspring-boot.run.main-class=com.qsx.appoint.support.VisitAcceptanceApplication'],
                                   cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            wait_app(app, port)
            assert len(request(port, 'GET', '/api/v1/doctor/sessions', token=doctor_token)) == 4
            for item in visits:
                assert request(port, 'GET', f"/api/v1/appointments/{item['id']}", token=token)['visit']['canCheckIn']
            # 同一场次同时保留四种状态；验证非队首接诊且没有跨行忙碌限制。
            for item in visits[1:]:
                request(port, 'POST', f"/api/v1/appointments/{item['id']}/check-in", token=token)
            for item in visits[2:]:
                request(port, 'POST', f"/api/v1/doctor/appointments/{item['id']}/start-visit", token=doctor_token)
            request(port, 'POST', f"/api/v1/doctor/appointments/{visits[3]['id']}/complete-visit", token=doctor_token)
            rows = request(port, 'GET', f'/api/v1/doctor/appointments?date={date}&sessionType=MORNING_FIRST', token=doctor_token)
            assert [row['appointmentId'] for row in rows] == [item['id'] for item in visits]
            assert [row['visitNumber'] for row in rows] == sorted(item['visitNumber'] for item in visits)
            expected_states = ('NOT_CHECKED_IN', 'WAITING', 'IN_PROGRESS', 'COMPLETED')
            for row, state in zip(rows, expected_states):
                assert row['visitStatus'] == state
                assert row['canStartVisit'] == (state == 'WAITING')
                assert row['canCompleteVisit'] == (state == 'IN_PROGRESS')
                user_detail = request(port, 'GET', f"/api/v1/appointments/{row['appointmentId']}", token=token)
                doctor_detail = request(port, 'GET', f"/api/v1/doctor/appointments/{row['appointmentId']}", token=doctor_token)
                assert '*' in doctor_detail['patient']['documentNoMasked']
                for field in ('status', 'checkedInAt', 'startedAt', 'completedAt'):
                    assert user_detail['visit'][field] == doctor_detail['visit'][field]
                assert user_detail['status'] == 'CONFIRMED' and user_detail['paymentStatus'] == 'PAID'
            overview = request(port, 'GET', '/api/v1/doctor/sessions', token=doctor_token)[0]
            assert overview['appointmentDate'] == date and overview['confirmedCount'] == 4
            assert [overview[key] for key in ('notCheckedInCount', 'waitingCount', 'inProgressCount', 'completedCount')] == [1, 1, 1, 1]
            desc = request(port, 'GET', '/api/v1/appointments/my', token=token)
            asc = request(port, 'GET', '/api/v1/appointments/my?order=ASC', token=token)
            assert [item['id'] for item in desc] == list(reversed([item['id'] for item in asc]))
            assert len(desc) == 6 and any(item['status'] == 'CANCELLED' for item in desc) and any(item['status'] == 'EXPIRED' for item in desc)
            assert mysql(snapshots_sql) == before
            # 再推进剩余人员至终态，验证整个用户/医生操作闭环。
            for item in visits[:2]:
                if item == visits[0]:
                    request(port, 'POST', f"/api/v1/appointments/{item['id']}/check-in", token=token)
                    request(port, 'POST', f"/api/v1/appointments/{item['id']}/check-in", token=token, expected=409)
                request(port, 'POST', f"/api/v1/doctor/appointments/{item['id']}/start-visit", token=doctor_token)
            for item in visits[:3]:
                request(port, 'POST', f"/api/v1/doctor/appointments/{item['id']}/complete-visit", token=doctor_token)
                assert request(port, 'GET', f"/api/v1/appointments/{item['id']}", token=token)['visit']['status'] == 'COMPLETED'
            final_rows = request(port, 'GET', f'/api/v1/doctor/appointments?date={date}&sessionType=MORNING_FIRST', token=doctor_token)
            assert [row['appointmentId'] for row in final_rows] == [item['id'] for item in visits]
            assert all(row['visitStatus'] == 'COMPLETED' and not row['canStartVisit'] and not row['canCompleteVisit'] for row in final_rows)
            final_sessions = request(port, 'GET', '/api/v1/doctor/sessions', token=doctor_token)
            assert len(final_sessions) == 4 and final_sessions[0]['completedCount'] == final_sessions[0]['confirmedCount'] == 4
            final_user = request(port, 'GET', f"/api/v1/appointments/{visits[3]['id']}", token=token)
            final_doctor = request(port, 'GET', f"/api/v1/doctor/appointments/{visits[3]['id']}", token=doctor_token)
            for field in ('status', 'checkedInAt', 'startedAt', 'completedAt'):
                assert final_user['visit'][field] == final_doctor['visit'][field]
            request(port, 'POST', f"/api/v1/doctor/appointments/{visits[3]['id']}/complete-visit", token=doctor_token, expected=409)
            assert mysql(snapshots_sql) == before
            success = True
            print('Compose 验收通过：预约、支付、消息确认、取消退款、迟到支付、医生绑定、当天签到、四状态看板、接诊完成及库存快照守恒。')
            print(f'HTTP 客户端：{HTTP_CLIENT}，业务请求：{len(HTTP_RECORDS)} 次。')
            if HTTP_REPORT:
                print(f'脱敏 HTTP 记录：{HTTP_REPORT}')
        finally:
            stop_app(app)
            if success:
                rabbit('delete_vhost', VHOST)
                mysql(f"DROP DATABASE `{DB}`; DROP USER '{USER}'@'%';")
            else:
                print(f'验收失败，保留隔离数据库 {DB}、vhost {VHOST} 和日志 {LOG}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--http-client', choices=('urllib', 'curl'), default='urllib')
    parser.add_argument('--http-report', type=Path, help='写入脱敏的业务 HTTP 请求结果 JSON')
    options = parser.parse_args()
    HTTP_CLIENT, HTTP_REPORT = options.http_client, options.http_report
    main()
