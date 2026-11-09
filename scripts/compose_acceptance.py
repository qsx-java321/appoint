#!/usr/bin/env python3
"""在 Compose 依赖上运行隔离的真实 HTTP 验收。"""
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
            if response.status != expected:
                raise RuntimeError(f'{method} {path}: HTTP {response.status}, expected {expected}')
            return result.get('data')
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f'{method} {path}: HTTP {exc.code}, expected {expected}') from exc


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
                'APPOINT_EXPIRATION_ENABLED': 'false'})
    success = False
    with LOG.open('w') as log:
        app = subprocess.Popen(['./mvnw', '-o', 'spring-boot:run'], cwd=ROOT, env=env,
                               stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            deadline = time.monotonic() + 120
            while True:
                if app.poll() is not None:
                    raise RuntimeError('应用启动失败')
                try:
                    with urllib.request.urlopen(f'http://127.0.0.1:{port}/api/v1/hospitals', timeout=2):
                        break
                except urllib.error.HTTPError as exc:
                    if exc.code == 401:
                        break
                except OSError:
                    pass
                if time.monotonic() >= deadline:
                    raise RuntimeError('应用启动超时')
                time.sleep(1)
            email = RUN + '@example.com'
            credentials = {'email': email, 'password': 'password123'}
            request(port, 'POST', '/api/v1/auth/register', credentials, expected=201)
            token = request(port, 'POST', '/api/v1/auth/login', credentials)['accessToken']
            patient = request(port, 'POST', '/api/v1/patients', {
                'name': '验收就诊人', 'documentType': 'RESIDENT_ID_CARD',
                'documentNo': '110105199001010010', 'contactPhone': '13800138000',
                'contactEmail': 'patient@example.com'}, token, expected=201)['id']
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
            success = True
            print('Compose 验收通过：预约、支付、消息确认、取消、退款、迟到支付释放及库存守恒。')
        finally:
            if app.poll() is None:
                os.killpg(app.pid, signal.SIGTERM)
                try:
                    app.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    os.killpg(app.pid, signal.SIGKILL)
                    app.wait()
            if success:
                rabbit('delete_vhost', VHOST)
                mysql(f"DROP DATABASE `{DB}`; DROP USER '{USER}'@'%';")
            else:
                print(f'验收失败，保留隔离数据库 {DB}、vhost {VHOST} 和日志 {LOG}')


if __name__ == '__main__':
    main()
