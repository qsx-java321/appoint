#!/usr/bin/env python3
"""管理常驻dev/test/demo；此入口不用于自动化回归。凭据只存私密文件。"""
import argparse
import json
import os
import secrets
import signal
import socket
import subprocess
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / '.local-env'
COMPOSE = ['docker', 'compose', '--env-file', 'compose.env']
PORTS = {'dev': (8080, 5173), 'test': (8081, 5174), 'demo': (8082, 5175)}


def read_properties(path):
    return dict(line.strip().split('=', 1) for line in path.read_text().splitlines()
                if line.strip() and not line.lstrip().startswith('#') and '=' in line)


def private_write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(fd, 'w') as stream:
        stream.write(text)


def run(args, data=None):
    result = subprocess.run(args, cwd=ROOT, input=data, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError('资源管理命令失败；请核对服务状态及本地凭据（不输出含密配置）')
    return result.stdout.strip()


def mysql(sql):
    return run(COMPOSE + ['exec', '-T', 'mysql', 'sh', '-c',
                         'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot -N -B'], sql)


def rabbit(*args):
    return run(COMPOSE + ['exec', '-T', 'rabbitmq', 'rabbitmqctl', '-q', *args])


def init():
    if not (ROOT / 'compose.env').exists():
        raise RuntimeError('请先从compose.env.example建立compose.env')
    run(COMPOSE + ['up', '-d', '--wait'])
    config = json.loads(run(COMPOSE + ['config', '--format', 'json']))
    mysql_config = config['services']['mysql']['environment']
    for name in PORTS:
        path = ROOT / ('.env.' + name)
        if not path.exists():
            values = {'MYSQL_USER': 'appoint_' + name, 'MYSQL_PASSWORD': secrets.token_hex(24),
                      'RABBITMQ_USER': 'appoint_' + name, 'RABBITMQ_PASSWORD': secrets.token_hex(24),
                      'JWT_SECRET': secrets.token_urlsafe(48), 'MOCK_PAY_SIGNING_KEY': secrets.token_urlsafe(48),
                      'LOCAL_SEED_PASSWORD': secrets.token_urlsafe(18)}
            if name == 'dev':
                values['MYSQL_USER'] = mysql_config['MYSQL_USER']
                values['MYSQL_PASSWORD'] = mysql_config['MYSQL_PASSWORD']
            private_write(path, ''.join(f'{key}={value}\n' for key, value in values.items()))
        values = read_properties(path)
        if name != 'dev' and values['MYSQL_USER'] != 'appoint_' + name:
            raise RuntimeError('test/demo必须使用各自专用数据库账号')
        if values['RABBITMQ_USER'] != 'appoint_' + name:
            raise RuntimeError('必须使用环境专用RabbitMQ账号')
        db = 'appoint' if name == 'dev' else 'appoint_' + name
        user, password = values['MYSQL_USER'], values['MYSQL_PASSWORD']
        if not all(c.isalnum() or c in '_-' for c in user): raise RuntimeError('数据库账号格式不支持')
        if name != 'dev':
            # SQL字符串按MySQL NO_BACKSLASH_ESCAPES处理，生成密码本身仅十六进制。
            escaped = password.replace('\\', '\\\\').replace("'", "''")
            mysql(f"CREATE DATABASE IF NOT EXISTS `{db}`; CREATE USER IF NOT EXISTS '{user}'@'%' IDENTIFIED BY '{escaped}'; GRANT ALL PRIVILEGES ON `{db}`.* TO '{user}'@'%';")
        grants = mysql(f"SHOW GRANTS FOR '{user}'@'%';")
        for grant in grants.splitlines():
            if 'GRANT USAGE ON *.*' in grant: continue
            if f'ON `{db}`.* ' not in grant or 'WITH GRANT OPTION' in grant:
                raise RuntimeError('数据库账号存在其他库或全局权限，请人工核对')
        # 验证卷内密码，不根据配置文件擅自ALTER已有账号。
        shell = 'read -r password; export MYSQL_PWD="$password"; exec mysql -h127.0.0.1 -u "$1" -N -B "$2" -e "SELECT 1"'
        run(COMPOSE + ['exec', '-T', 'mysql', 'sh', '-c', shell, 'verify', user, db], password + '\n')
        vhost = '/appoint-' + name
        if vhost not in rabbit('list_vhosts', 'name').splitlines(): rabbit('add_vhost', vhost)
        ruser, rpass = values['RABBITMQ_USER'], values['RABBITMQ_PASSWORD']
        users = [line.split()[0] for line in rabbit('list_users').splitlines() if line.strip()]
        # 密码经stdin进入容器shell，避免出现在宿主进程命令行。
        shell = 'read -r password; exec rabbitmqctl -q "$1" "$2" "$password"'
        if ruser not in users:
            run(COMPOSE + ['exec', '-T', 'rabbitmq', 'sh', '-c', shell, 'manage', 'add_user', ruser], rpass + '\n')
        run(COMPOSE + ['exec', '-T', 'rabbitmq', 'sh', '-c', shell, 'manage', 'authenticate_user', ruser], rpass + '\n')
        rabbit('set_permissions', '-p', vhost, ruser, '.*', '.*', '.*')
        grants = rabbit('list_user_permissions', ruser)
        if any(line.split()[0] != vhost for line in grants.splitlines() if line.startswith('/')):
            raise RuntimeError('环境RabbitMQ账号还拥有其他vhost权限，请人工核对')
        print(f'{name}: 资源准备完成；未清理业务表、缓存或消息')
    print('dev切换前必须执行维护预检；已有环境配置及数据卷保持原样。')


def clean_env(name):
    env = {key: value for key, value in os.environ.items()
           if not key.startswith(('SPRING_', 'APPOINT_', 'DB_', 'MYSQL_', 'RABBITMQ_', 'REDIS_', 'SERVER_', 'JWT_', 'CATALOG_', 'MOCK_PAY_'))}
    env['SPRING_PROFILES_ACTIVE'] = name
    return env


def command_for(name, reset=False):
    args = ['./mvnw', '-o', 'spring-boot:run']
    options = [f'--spring.profiles.active={name}']
    if reset: options += ['--appoint.local-environment.reset-only=true', '--spring.main.web-application-type=none', '--appoint.messaging.enabled=false', '--appoint.outbox.enabled=false', '--appoint.expiration.enabled=false', '--appoint.reconciliation.enabled=false']
    args += ['-Dspring-boot.run.arguments=' + ' '.join(options)]
    return args


def alive(pid):
    try: os.kill(pid, 0); return True
    except ProcessLookupError: return False


def status(name):
    path = STATE / (name + '.json')
    record = json.loads(path.read_text()) if path.exists() else {}
    active = {}
    for key, value in record.items():
        if isinstance(value, dict) and alive(value['pid']):
            current = Path('/proc') / str(value['pid']) / 'stat'
            if current.exists() and current.read_text().split()[21] == value['startTicks']:
                active[key] = value['pid']
    return active


def start(name):
    if status(name): raise RuntimeError('脚本已记录运行实例')
    if name == 'dev' and not (STATE / 'dev-cutover-ready').exists():
        raise RuntimeError('请先停止旧dev并执行cutover-dev维护预检')
    for port in PORTS[name]:
        with socket.socket() as sock:
            if sock.connect_ex(('127.0.0.1', port)) == 0: raise RuntimeError(f'端口{port}已占用')
    STATE.mkdir(exist_ok=True)
    processes = {}
    try:
        with (STATE / (name + '-backend.log')).open('a') as log:
            app = subprocess.Popen(command_for(name), cwd=ROOT, env=clean_env(name), stdout=log,
                                   stderr=subprocess.STDOUT, start_new_session=True)
        processes['backend'] = app.pid
        deadline = time.monotonic() + 120
        while True:
            if app.poll() is not None: raise RuntimeError('后端启动失败，请查看该环境日志')
            with socket.socket() as sock:
                if sock.connect_ex(('127.0.0.1', PORTS[name][0])) == 0: break
            if time.monotonic() > deadline: raise RuntimeError('后端启动超时')
            time.sleep(.5)
        with (STATE / (name + '-frontend.log')).open('a') as log:
            front = subprocess.Popen(['npm', 'run', 'dev' if name == 'dev' else 'dev:' + name], cwd=ROOT/'frontend',
                                     stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        processes['frontend'] = front.pid
        time.sleep(2)
        if front.poll() is not None: raise RuntimeError('前端启动失败，请检查Node环境与日志')
        (STATE / (name + '.json')).write_text(json.dumps({key:{'pid':pid,'startTicks':(Path('/proc')/str(pid)/'stat').read_text().split()[21]} for key,pid in processes.items()}))
    except BaseException:
        for pid in processes.values():
            if alive(pid): os.killpg(pid, signal.SIGTERM)
        raise
    print(f'{name}: http://127.0.0.1:{PORTS[name][1]}')


def stop(name):
    owned = list(status(name).values())
    for pid in owned:
        # 防止PID复用：必须是本项目工作目录及预期启动命令。
        proc = Path('/proc') / str(pid)
        cwd = (proc/'cwd').resolve()
        command = (proc/'cmdline').read_bytes()
        if cwd not in (ROOT, ROOT/'frontend') or not any(x in command for x in (b'maven', b'npm', b'mvnw')):
            raise RuntimeError('PID归属变化，拒绝停止')
        os.killpg(pid, signal.SIGTERM)
    deadline = time.monotonic() + 25
    while any(alive(pid) and (Path('/proc')/str(pid)/'stat').read_text().split()[2] != 'Z' for pid in owned) and time.monotonic() < deadline:
        time.sleep(.2)
    if any(alive(pid) and (Path('/proc')/str(pid)/'stat').read_text().split()[2] != 'Z' for pid in owned):
        raise RuntimeError('进程尚未停止，保留PID记录以供核查')
    (STATE/(name+'.json')).unlink(missing_ok=True)
    print(f'{name}: 已发送停止信号；共享Compose保持运行')


def cutover():
    with socket.socket() as sock:
        if sock.connect_ex(('127.0.0.1',8080)) == 0: raise RuntimeError('先停止旧dev；未执行切换')
    pending = mysql("USE appoint; SELECT (SELECT COUNT(*) FROM payment_outbox_events WHERE status<>'PUBLISHED') + (SELECT COUNT(*) FROM payment_orders WHERE status IN ('PAYING','REFUND_PENDING')) + (SELECT COUNT(*) FROM payment_refunds WHERE status<>'SUCCEEDED') + (SELECT COUNT(*) FROM payment_attempts WHERE status IN ('CREATING','PROCESSING')) + (SELECT COUNT(*) FROM payment_orders p JOIN appointment_orders a ON a.id=p.appointment_order_id WHERE p.status='PAID' AND a.status<>'CONFIRMED');")
    if pending != '0': raise RuntimeError('存在未完成资金/Outbox工作，请先定位收敛；未执行切换')
    queues = rabbit('list_queues','-p','/','name','messages_ready','messages_unacknowledged')
    for line in queues.splitlines():
        columns = line.split()
        if len(columns) == 3 and columns[1].isdigit() and (columns[1] != '0' or columns[2] != '0'):
            raise RuntimeError('旧vhost仍有消息，请先定位收敛；未执行切换')
    STATE.mkdir(exist_ok=True)
    (STATE/'dev-cutover-ready').write_text('旧应用停止且资金/消息预检通过；保留旧vhost/cache与数据卷\n')
    print('dev切换预检通过；未修改业务数据或清理旧资源')


def clock(action, instant):
    values = read_properties(ROOT/'.env.test')
    base = 'http://127.0.0.1:8081/api/v1'
    data = json.dumps({'email':'test-admin@example.local','password':values['LOCAL_SEED_PASSWORD']}).encode()
    req = urllib.request.Request(base+'/auth/login',data=data,headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req) as response: token = json.load(response)['data']['accessToken']
    method = {'get':'GET','set':'PUT','reset':'DELETE'}[action]
    data = json.dumps({'instant':instant}).encode() if action == 'set' else None
    req = urllib.request.Request(base+'/test-control/clock',data=data,method=method,
                                 headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'})
    with urllib.request.urlopen(req) as response: print(json.dumps(json.load(response)['data'],ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command',required=True)
    sub.add_parser('init'); sub.add_parser('cutover-dev')
    for command in ('start','stop','status','reset'):
        p = sub.add_parser(command); p.add_argument('environment',choices=('test',) if command == 'reset' else PORTS)
    p = sub.add_parser('clock');p.add_argument('environment',choices=('test',));p.add_argument('action',choices=('get','set','reset'));p.add_argument('--instant')
    args = parser.parse_args()
    if args.command == 'init': init()
    elif args.command == 'cutover-dev': cutover()
    elif args.command == 'start': start(args.environment)
    elif args.command == 'stop': stop(args.environment)
    elif args.command == 'status': print(json.dumps(status(args.environment)))
    elif args.command == 'reset':
        if status('test'): raise RuntimeError('先停止test')
        subprocess.run(command_for('test',True),cwd=ROOT,env=clean_env('test'),check=True)
    elif args.command == 'clock': clock(args.action,args.instant)


if __name__ == '__main__':
    try: main()
    except Exception as error: raise SystemExit(str(error))
