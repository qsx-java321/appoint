#!/usr/bin/env python3
"""从模板初始化并管理常驻dev/test/demo；此入口不用于自动化回归。"""
import argparse
import fcntl
import json
import os
import signal
import socket
import subprocess
import tempfile
import time
import urllib.request
import uuid
from contextlib import contextmanager
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / '.local-env'
COMPOSE = ['docker', 'compose', '--env-file', 'compose.env']
ENVIRONMENTS = ('dev', 'test', 'demo')
BACKEND_PORT = 8080
FRONTEND_PORT = 5173
BACKEND_URL = f'http://127.0.0.1:{BACKEND_PORT}/api/v1'
FRONTEND_URL = f'http://127.0.0.1:{FRONTEND_PORT}'
ENVIRONMENT_HEADER = 'X-Appoint-Environment'
HTTP = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def read_properties(path):
    return dict(line.strip().split('=', 1) for line in path.read_text().splitlines()
                if line.strip() and not line.lstrip().startswith('#') and '=' in line)


def private_write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(fd, 'w') as stream:
        stream.write(text)


def ensure_config(name):
    path = ROOT / name
    if path.exists():
        return
    template = ROOT / (name + '.example')
    if not template.is_file():
        raise RuntimeError(f'缺少配置模板{template.name}；未启动共享服务')
    try:
        private_write(path, template.read_text())
    except FileExistsError:
        # 并行初始化时保留另一进程刚创建的配置。
        pass


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
    for name in ('compose.env', '.env.dev', '.env.test', '.env.demo'):
        ensure_config(name)
    run(COMPOSE + ['up', '-d', '--wait'])
    for name in ENVIRONMENTS:
        path = ROOT / ('.env.' + name)
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


def clean_env(name):
    env = {key: value for key, value in os.environ.items()
           if not key.startswith(('SPRING_', 'APPOINT_', 'DB_', 'MYSQL_', 'RABBITMQ_', 'REDIS_', 'SERVER_', 'JWT_', 'CATALOG_', 'MOCK_PAY_'))}
    env['SPRING_PROFILES_ACTIVE'] = name
    return env


def command_for(name, reset=False):
    args = ['./mvnw', 'spring-boot:run']
    options = [f'--spring.profiles.active={name}']
    if reset: options += ['--appoint.local-environment.reset-only=true', '--spring.main.web-application-type=none', '--appoint.expiration.enabled=false']
    args += ['-Dspring-boot.run.arguments=' + ' '.join(options)]
    return args


@contextmanager
def operation_lock():
    STATE.mkdir(exist_ok=True)
    with (STATE / 'runtime.lock').open('a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError('已有启停或维护操作正在执行，请等待当前操作结束') from None
        yield


def process_info(pid):
    try:
        fields = (Path('/proc') / str(pid) / 'stat').read_text().rsplit(')', 1)[1].split()
        return fields[0], int(fields[2]), int(fields[3]), int(fields[19])
    except (FileNotFoundError, ProcessLookupError):
        return None


def group_members(record):
    pid = record['pid']
    start_ticks = int(record['startTicks'])
    leader = process_info(pid)
    if leader:
        if leader[1:] != (pid, pid, start_ticks):
            return []
        if leader[0] != 'Z':
            return [pid]

    # Maven/npm可能先于同组的Java/Node退出，孤留的子进程仍属于原会话。
    members = []
    for proc in Path('/proc').iterdir():
        if proc.name.isdecimal():
            info = process_info(proc.name)
            if info and info[0] != 'Z' and info[1:3] == (pid, pid) and info[3] >= start_ticks:
                members.append(int(proc.name))
    return members


def read_record(name):
    path = STATE / (name + '.json')
    try:
        return json.loads(path.read_text())
    except FileNotFoundError:
        return {}


def write_record(name, record):
    path = STATE / (name + '.json')
    with tempfile.NamedTemporaryFile(mode='w', dir=STATE, delete=False) as temp:
        temporary = Path(temp.name)
        try:
            json.dump(record, temp)
            temp.close()
            temporary.replace(path)
        finally:
            temporary.unlink(missing_ok=True)


def status(name):
    return {role: record['pid'] for role, record in read_record(name).items() if group_members(record)}


def check_port(port):
    with socket.socket() as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.bind(('127.0.0.1', port))
        except OSError as error:
            raise RuntimeError(f'端口{port}被占用或不可用，请从原入口停止相关进程并检查端口') from error


def require_idle():
    for name in ENVIRONMENTS:
        if status(name):
            raise RuntimeError(f'{name}仍有托管进程，请先执行stop {name}')
    check_port(BACKEND_PORT)
    check_port(FRONTEND_PORT)


def environment_status(url):
    with HTTP.open(url, timeout=2) as response:
        report = json.load(response)
        if not isinstance(report, dict):
            raise ValueError('无效的环境状态响应')
        return report


def wait_ready(process, url, name, launch_id, role, timeout):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f'{role}启动失败，请查看{name}的日志')
        try:
            report = environment_status(url)
        except (OSError, ValueError):
            time.sleep(.25)
            continue
        if report.get('profile') != name or report.get('launchId') != launch_id:
            raise RuntimeError(f'{role}响应来自其他运行实例，请从原入口停止相关进程')
        if report.get('ready') is True and process.poll() is None:
            return
        time.sleep(.25)
    raise RuntimeError(f'{role}启动超时，请查看{name}的日志')


def stop_groups(records, verify_owner):
    groups = []
    for role, record in records.items():
        members = group_members(record)
        if not members:
            continue
        if verify_owner:
            directory = ROOT if role == 'backend' else ROOT / 'frontend'
            proc = Path('/proc') / str(members[0])
            try:
                cwd = (proc / 'cwd').resolve(strict=True)
                command = (proc / 'cmdline').read_bytes()
            except (FileNotFoundError, ProcessLookupError):
                continue
            markers = (b'maven', b'mvnw', b'com.qsx.appoint.AppointApplication') if role == 'backend' else (b'npm', b'vite')
            if cwd != directory or not any(marker in command for marker in markers):
                raise RuntimeError('进程归属变化，保留记录以供核查')
        groups.append(record)

    for record in groups:
        if group_members(record):
            try:
                os.killpg(record['pid'], signal.SIGTERM)
            except ProcessLookupError:
                pass

    deadline = time.monotonic() + 25
    while any(group_members(record) for record in records.values()):
        if time.monotonic() >= deadline:
            raise RuntimeError('进程组尚未退出，保留记录以供核查')
        time.sleep(.2)


def start(name):
    require_idle()
    run(COMPOSE + ['up', '-d', '--wait', 'rabbitmq'])
    processes = []
    records = {}
    launch_id = uuid.uuid4().hex

    def launch(role, command, directory, env):
        env['APPOINT_LOCAL_LAUNCH_ID'] = launch_id
        with (STATE / f'{name}-{role}.log').open('a') as log:
            process = subprocess.Popen(command, cwd=directory, env=env, stdout=log,
                                       stderr=subprocess.STDOUT, start_new_session=True)
        processes.append(process)
        info = process_info(process.pid)
        if info is None:
            raise RuntimeError(f'{role}提前退出，请查看{name}的日志')
        records[role] = {'pid': process.pid, 'startTicks': info[3]}
        write_record(name, records)
        return process

    try:
        backend = launch('backend', command_for(name), ROOT, clean_env(name))
        # 首次克隆时为Maven下载依赖及后端初始化预留时间。
        wait_ready(backend, BACKEND_URL + '/local-environment', name, launch_id, '后端', 600)
        npm_script = 'dev' if name == 'dev' else 'dev:' + name
        frontend = launch('frontend', ['npm', 'run', npm_script], ROOT / 'frontend', os.environ.copy())
        wait_ready(frontend, FRONTEND_URL + '/__local/ready', name, launch_id, '前端', 30)
    except BaseException:
        stop_groups(records, verify_owner=False)
        (STATE / (name + '.json')).unlink(missing_ok=True)
        for process in processes:
            process.poll()
        raise
    print(f'{name}: {FRONTEND_URL}')


def stop(name):
    stop_groups(read_record(name), verify_owner=True)
    (STATE / (name + '.json')).unlink(missing_ok=True)
    print(f'{name}: 前后端已停止，共享Compose继续运行')


def clock(action, instant):
    try:
        report = environment_status(BACKEND_URL + '/local-environment')
    except (OSError, ValueError):
        raise RuntimeError('请先启动已就绪的test后端') from None
    if report.get('profile') != 'test' or report.get('ready') is not True:
        raise RuntimeError('调时只适用于运行中的test环境')
    values = read_properties(ROOT / '.env.test')
    headers = {'Content-Type': 'application/json', ENVIRONMENT_HEADER: 'test'}
    data = json.dumps({'email': 'test-admin@example.local', 'password': values['LOCAL_SEED_PASSWORD']}).encode()
    req = urllib.request.Request(BACKEND_URL + '/auth/login', data=data, headers=headers)
    with HTTP.open(req, timeout=15) as response:
        token = json.load(response)['data']['accessToken']
    method = {'get': 'GET', 'set': 'PUT', 'reset': 'DELETE'}[action]
    data = json.dumps({'instant': instant}).encode() if action == 'set' else None
    headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BACKEND_URL + '/test-control/clock', data=data, method=method, headers=headers)
    with HTTP.open(req, timeout=15) as response:
        print(json.dumps(json.load(response)['data'], ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('init')
    sub.add_parser('start').add_argument('environment', choices=ENVIRONMENTS, nargs='?', default='demo')
    for command in ('stop', 'status'):
        sub.add_parser(command).add_argument('environment', choices=ENVIRONMENTS)
    sub.add_parser('reset').add_argument('environment', choices=('dev', 'test'))
    p = sub.add_parser('clock')
    p.add_argument('environment', choices=('test',))
    p.add_argument('action', choices=('get', 'set', 'reset'))
    p.add_argument('--instant')
    args = parser.parse_args()

    if args.command == 'init':
        init()
    elif args.command == 'status':
        print(json.dumps(status(args.environment)))
    elif args.command == 'clock':
        clock(args.action, args.instant)
    else:
        with operation_lock():
            if args.command == 'start':
                start(args.environment)
            elif args.command == 'stop':
                stop(args.environment)
            elif args.command == 'reset':
                require_idle()
                subprocess.run(command_for(args.environment, True), cwd=ROOT, env=clean_env(args.environment), check=True)


if __name__ == '__main__':
    try: main()
    except Exception as error: raise SystemExit(str(error))
