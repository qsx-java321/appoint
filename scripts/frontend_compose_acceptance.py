#!/usr/bin/env python3
"""真实Vue/Compose隔离验收；正常8080/5173开发服务不受影响。"""
import argparse
import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import secrets
import shutil
import signal
import socket
import subprocess
import time
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('compose_acceptance', ROOT / 'scripts/compose_acceptance.py')
compose = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compose)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--business-date', default=str(datetime.datetime.now(ZoneInfo('Asia/Shanghai')).date()))
    args = parser.parse_args()
    date = datetime.date.fromisoformat(args.business_date)
    run = secrets.token_hex(6)
    db, user, vhost, namespace = 'front_' + run, 'f_' + run, 'front-' + run, 'front-' + run
    evidence = ROOT / '.scratch/frontend-compose-live/evidence' / run
    runtime = ROOT / '.scratch/frontend-compose-live/runtime' / run
    evidence.mkdir(parents=True, exist_ok=True)
    runtime.mkdir(parents=True, mode=0o700, exist_ok=True)
    node = shutil.which('node')
    npm = shutil.which('npm')
    if not node or not npm:
        raise RuntimeError('请先在当前终端加载本机Node/npm，例如nvm use。')
    resources = {'runId': run, 'database': db, 'databaseUser': user, 'rabbitVhost': vhost,
                 'redisNamespace': namespace, 'businessDate': str(date), 'businessTime': '13:40:00',
                 'backendPort': 18082, 'frontendPort': 5174, 'normalPorts': [8080, 5173]}
    (evidence / 'resources.json').write_text(json.dumps(resources, ensure_ascii=False, indent=2) + '\n')
    (runtime.parent / 'latest.json').write_text(json.dumps({'evidence': str(evidence), 'runtime': str(runtime)}))
    print(json.dumps({'runId': run, 'evidence': str(evidence)}, ensure_ascii=False), flush=True)
    compose.command('docker', 'compose', '--env-file', 'compose.env', 'config', '--quiet')
    config = json.loads(compose.command('docker', 'compose', '--env-file', 'compose.env', 'config', '--format', 'json', capture=True))
    rabbit = config['services']['rabbitmq']['environment']
    password = secrets.token_hex(24)
    compose.mysql(f"CREATE DATABASE `{db}`; CREATE USER '{user}'@'%' IDENTIFIED BY '{password}'; GRANT ALL PRIVILEGES ON `{db}`.* TO '{user}'@'%';")
    compose.rabbit('add_vhost', vhost)
    compose.rabbit('set_permissions', '-p', vhost, rabbit['RABBITMQ_DEFAULT_USER'], '.*', '.*', '.*')
    env = os.environ.copy()
    env.update({
        'SPRING_CONFIG_IMPORT': '', 'SPRING_PROFILES_ACTIVE': 'dev', 'SERVER_PORT': '18082',
        'SPRING_DATASOURCE_URL': f'jdbc:mysql://127.0.0.1:3306/{db}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai',
        'SPRING_DATASOURCE_USERNAME': user, 'SPRING_DATASOURCE_PASSWORD': password,
        'SPRING_DATASOURCE_DRIVER_CLASS_NAME': 'com.mysql.cj.jdbc.Driver',
        'SPRING_RABBITMQ_HOST': '127.0.0.1', 'SPRING_RABBITMQ_PORT': '5672',
        'SPRING_RABBITMQ_USERNAME': rabbit['RABBITMQ_DEFAULT_USER'],
        'SPRING_RABBITMQ_PASSWORD': rabbit['RABBITMQ_DEFAULT_PASS'], 'SPRING_RABBITMQ_VIRTUAL_HOST': vhost,
        'SPRING_RABBITMQ_PUBLISHER_CONFIRM_TYPE': 'correlated', 'SPRING_RABBITMQ_PUBLISHER_RETURNS': 'true',
        'SPRING_DATA_REDIS_HOST': '127.0.0.1', 'SPRING_DATA_REDIS_PORT': '6379',
        'APPOINT_CATALOG_CACHE_ENABLED': 'true', 'APPOINT_CATALOG_CACHE_NAMESPACE': namespace,
        'APPOINT_MESSAGING_ENABLED': 'true', 'APPOINT_OUTBOX_ENABLED': 'true',
        'APPOINT_OUTBOX_SCAN_DELAY': '250ms', 'APPOINT_EXPIRATION_ENABLED': 'false',
        'APPOINT_RECONCILIATION_ENABLED': 'false',
        'APPOINT_ACCEPTANCE_INSTANT': str(date) + 'T05:40:00Z',
        'APPOINT_JWT_SECRET': secrets.token_urlsafe(48), 'APPOINT_MOCK_PAY_SIGNING_KEY': secrets.token_urlsafe(48),
    })
    app = None
    success = False
    with (runtime / 'backend.log').open('w') as log:
        try:
            app = subprocess.Popen(['./mvnw', '-o', 'spring-boot:test-run',
                                    '-Dspring-boot.run.main-class=com.qsx.appoint.support.VisitAcceptanceApplication'],
                                   cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            compose.wait_app(app, 18082)
            actors = {}
            actor_password = 'Compose-' + secrets.token_hex(10)
            for role in ('admin', 'doctor'):
                email = f'front-{run}-{role}@example.com'
                actor = compose.request(18082, 'POST', '/api/v1/auth/register', {'email': email, 'password': actor_password}, expected=201)
                actors[role] = {'email': email, 'password': actor_password, 'id': actor['id']}
            compose.mysql(f"USE `{db}`; INSERT INTO user_roles(user_id,role_id) SELECT {int(actors['admin']['id'])},id FROM roles WHERE code='ADMIN';")
            actors['user'] = {'email': f'front-{run}-user@example.com', 'password': actor_password}
            private = runtime / 'actors.json'
            private.write_text(json.dumps({'actors': actors, 'runId': run, 'date': str(date),
                                           'documentNo': compose.sample_document(731), 'evidence': str(evidence)}))
            private.chmod(0o600)
            test_env = os.environ.copy()
            test_env.update({'COMPOSE_REAL_TEST': '1', 'COMPOSE_E2E_CONFIG': str(private),
                             'REAL_API_TARGET': 'http://127.0.0.1:18082',
                             'E2E_REPORT_FILE': str(evidence / 'browser-results.json')})
            with (evidence / 'browser.log').open('w') as browser_log:
                result = subprocess.run([npm, 'run', 'test:e2e'], cwd=ROOT / 'frontend', env=test_env,
                                        stdout=browser_log, stderr=subprocess.STDOUT)
            if result.returncode:
                raise RuntimeError(f'浏览器流程失败，查看{evidence / "browser.log"}，隔离资源保留。')
            deadline = time.monotonic() + 30
            while compose.mysql(f"USE `{db}`; SELECT COUNT(*) FROM payment_outbox_events WHERE status<>'PUBLISHED';") != '0':
                if time.monotonic() >= deadline:
                    raise RuntimeError('Outbox尚有未完成发布的事件。')
                time.sleep(.5)
            audit = {
                'orders': compose.mysql(f"USE `{db}`; SELECT a.id,a.status,p.status,COALESCE(a.visit_number,0),COALESCE(v.status,'NONE') FROM appointment_orders a JOIN payment_orders p ON p.appointment_order_id=a.id LEFT JOIN visit_records v ON v.appointment_order_id=a.id ORDER BY a.id;"),
                'inventory': compose.mysql(f"USE `{db}`; SELECT id,total_capacity,available_capacity,held_capacity,confirmed_capacity,next_visit_number FROM schedules ORDER BY id;"),
                'outbox': compose.mysql(f"USE `{db}`; SELECT event_type,status,COUNT(*) FROM payment_outbox_events GROUP BY event_type,status;"),
                'inbox': compose.mysql(f"USE `{db}`; SELECT event_type,COUNT(*) FROM coordination_inbox_events GROUP BY event_type;"),
                'refunds': compose.mysql(f"USE `{db}`; SELECT status,COUNT(*) FROM payment_refunds GROUP BY status;"),
            }
            (evidence / 'database-audit.json').write_text(json.dumps(audit, ensure_ascii=False, indent=2)+'\n')
            assert compose.mysql(f"USE `{db}`; SELECT COUNT(*) FROM schedules WHERE total_capacity<>available_capacity+held_capacity+confirmed_capacity;") == '0'
            assert compose.mysql(f"USE `{db}`; SELECT COUNT(*) FROM appointment_orders a JOIN payment_orders p ON p.appointment_order_id=a.id JOIN visit_records v ON v.appointment_order_id=a.id WHERE a.status='CONFIRMED' AND p.status='PAID' AND v.status='COMPLETED';") == '1'
            assert compose.mysql(f"USE `{db}`; SELECT COUNT(*) FROM appointment_orders a JOIN payment_orders p ON p.appointment_order_id=a.id WHERE a.status='CANCELLED' AND p.status='REFUNDED';") == '1'
            assert compose.mysql(f"USE `{db}`; SELECT COUNT(*) FROM appointment_active_claims;") == '1'
            keys = compose.redis_cli('--scan', '--pattern', f'appoint:{namespace}:catalog:v1:*').splitlines()
            assert keys, '未观察到真实Redis目录缓存'
            (evidence / 'redis-check.json').write_text(json.dumps({'namespace': namespace, 'catalogKeyCount': len(keys)}, indent=2)+'\n')
            queues = compose.command('docker', 'compose', '--env-file', 'compose.env', 'exec', '-T', 'rabbitmq',
                                     'rabbitmqctl', '-q', 'list_queues', '-p', vhost, 'name', 'messages_ready',
                                     'messages_unacknowledged', 'consumers', capture=True)
            (evidence / 'rabbit-queues.txt').write_text(queues)
            for line in queues.splitlines():
                columns = line.split('\t')
                if len(columns) == 4 and columns[0].endswith('.dlq'):
                    assert columns[1] == '0' and columns[2] == '0', '本轮隔离队列出现死信'
            success = True
        finally:
            if app:
                compose.stop_app(app)
            if success:
                keys = compose.redis_cli('--scan', '--pattern', f'appoint:{namespace}:catalog:v1:*').splitlines()
                if keys:
                    compose.redis_cli('DEL', *keys)
                compose.rabbit('delete_vhost', vhost)
                compose.mysql(f"DROP DATABASE `{db}`; DROP USER '{user}'@'%';")
                private.unlink(missing_ok=True)
            (evidence / 'cleanup.json').write_text(json.dumps({'success': success, 'isolatedApplicationStopped': True,
                                    'isolatedResourcesRemoved': success, 'normalServicesStopped': False}, indent=2)+'\n')
    manifest = []
    for base in [ROOT/'frontend/src', ROOT/'frontend/tests/e2e']:
        for path in sorted(base.rglob('*')):
            if path.is_file(): manifest.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+str(path.relative_to(ROOT)))
    (evidence/'source-manifest.sha256').write_text('\n'.join(manifest)+'\n')
    print(json.dumps({'passed': success, 'evidence': str(evidence), 'normalServices': 'running'}, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    main()
