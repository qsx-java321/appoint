"""一次性正式 test 环境业务验收；凭据仅存 .local-env，证据脱敏。"""
import concurrent.futures
import datetime as dt
import json
import secrets
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import local_env as env

OUT = Path(__file__).parent / 'evidence'
OUT.mkdir(exist_ok=True)
RUN = dt.datetime.now(ZoneInfo('Asia/Shanghai')).strftime('%Y%m%d-%H%M%S')
DAY = dt.datetime.now(ZoneInfo('Asia/Shanghai')).date()
requests, checks, records = [], [], {}
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

def save():
    (OUT / 'http.json').write_text(json.dumps(requests, ensure_ascii=False, indent=2)+'\n')
    (OUT / 'checks.json').write_text(json.dumps(checks, ensure_ascii=False, indent=2)+'\n')
    (OUT / 'records.json').write_text(json.dumps({'run': RUN, 'date': str(DAY), **records}, ensure_ascii=False, indent=2)+'\n')

def clean(value):
    private = {'accessToken', 'password', 'signature', 'documentNo', 'contactPhone', 'contactEmail', 'email'}
    if isinstance(value, dict):
        return {k: '<REDACTED>' if k in private else clean(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)): return [clean(v) for v in value]
    return value

def api(method, path, body=None, token=None, expected=200, code=None, key=None):
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    if key: headers['Idempotency-Key'] = key
    req = urllib.request.Request('http://127.0.0.1:8081/api/v1'+path,
        data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        with opener.open(req, timeout=15) as response: status, data = response.status, json.load(response)
    except urllib.error.HTTPError as e: status, data = e.code, json.load(e)
    requests.append({'method': method, 'path': path, 'status': status, 'expected': expected, 'body': clean(data)})
    assert status == expected, (method, path, status, expected, clean(data))
    if code is not None: assert data['code'] == code, (path, data['code'], code)
    return data.get('data')

def check(name, condition=True):
    assert condition, name
    checks.append({'name': name, 'passed': True})
    save()
    print('PASS '+name, flush=True)

def login(actor): return api('POST', '/auth/login', actor)['accessToken']
def clock(t): return api('PUT', '/test-control/clock', {'instant': str(DAY)+'T'+t+'+08:00'}, admin)
def detail(order): return api('GET', '/appointments/'+order['appointmentOrderId'], token=user)
def wait(order, predicate, timeout=50):
    until = time.monotonic()+timeout
    while True:
        value = detail(order)
        if predicate(value): return value
        assert time.monotonic() < until, ('poll timeout', clean(value))
        time.sleep(.5)

def document(n):
    prefix = '11010519900101'+f'{n:03}'
    return prefix+'10X98765432'[sum(int(a)*b for a,b in zip(prefix,(7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2)))%11]

def patient(n):
    return api('POST', '/patients', {'name': f'流程验收{RUN}-{n}', 'documentType': 'RESIDENT_ID_CARD',
        'documentNo': document(n), 'contactPhone': '13800138000', 'contactEmail': 'synthetic@example.local'}, user, 201)

def book(label, p=None, day=0, session='AFTERNOON_FIRST', expected=201, code=None, key=None):
    body={'doctorId': doc['id'], 'patientId': (p or patients[0])['id'],
          'appointmentDate': str(DAY+dt.timedelta(days=day)), 'sessionType': session}
    result=api('POST', '/appointments', body, user, expected, code, key or str(uuid.uuid4()))
    if expected==201: records[label]=result
    return result

def attempt(order): return api('POST', '/payments/'+order['paymentOrderId']+'/attempts', {'channel':'MOCK'}, user, 202)
def notify(a, action='complete'):
    return api('POST', a['checkoutUrl'].removeprefix('/api/v1')+'/'+action, token=user, expected=202)
def pay(order):
    a=attempt(order); notify(a)
    return wait(order, lambda v: v['status']=='CONFIRMED')
def cancel(order, expected=200): return api('POST','/appointments/'+order['appointmentOrderId']+'/cancel', token=user, expected=expected)

seed=env.read_properties(ROOT/'.env.test')
actors={role:{'email':f'workflow-{RUN}-{role}@example.local','password':'Test-'+secrets.token_hex(12)} for role in ('user','other','doctor','otherDoctor','browserDoctor','browserUser')}
admin=login({'email':'test-admin@example.local','password':seed['LOCAL_SEED_PASSWORD']})
original_settings=api('GET','/admin/booking-settings',token=admin)
records['originalSettings']=original_settings
try:
    api('GET','/hospitals',expected=401,code=2001)
    api('POST','/auth/register',{'email':'bad','password':'short'},expected=400,code=1001)
    for role, actor in actors.items():
        if role=='browserUser': continue
        actor['id']=api('POST','/auth/register',actor,expected=201)['id']
    api('POST','/auth/register',{'email':actors['user']['email'].upper(),'password':actors['user']['password']},expected=409,code=3001)
    api('POST','/auth/login',{'email':actors['user']['email'],'password':'wrong'},expected=401,code=2003)
    user=login(actors['user']); other=login(actors['other'])
    check('注册、邮箱规范化去重、登录失败及未登录拦截')
    api('GET','/admin/booking-settings',token=user,expected=403,code=2002)
    api('PUT','/test-control/clock',{'instant':str(DAY)+'T00:00:00Z'},user,403,2002)
    clock('13:35:00')
    patients=[patient(n) for n in range(601,608)]
    api('POST','/patients',{'name':'重复','documentType':'RESIDENT_ID_CARD','documentNo':document(601),'contactPhone':'13800138000','contactEmail':'x@example.local'},user,409,4101)
    api('POST','/patients',{'name':'无效','documentType':'RESIDENT_ID_CARD','documentNo':'123','contactPhone':'13800138000','contactEmail':'x@example.local'},user,400,1001)
    api('GET','/patients/'+patients[0]['id'],token=other,expected=403,code=2002)
    check('就诊人新增、证件校验、重复登记和跨账号归属保护')
    h=api('POST','/admin/hospitals',{'name':'流程验收医院-'+RUN,'address':'虚构测试地址'},admin,201)
    dep=api('POST',f"/admin/hospitals/{h['id']}/departments",{'name':'流程验收科室'},admin,201)
    doc=api('POST',f"/admin/departments/{dep['id']}/doctors",{'name':'流程验收医生','title':'主任医师'},admin,201)
    doc2=api('POST',f"/admin/departments/{dep['id']}/doctors",{'name':'权限验收医生','title':'主治医师'},admin,201)
    records.update(hospital=h,department=dep,doctor=doc,otherDoctor=doc2)
    api('POST',f"/admin/hospitals/{h['id']}/departments",{'name':'流程验收科室'},admin,409,4401)
    api('DELETE','/admin/hospitals/'+h['id'],token=admin,expected=409,code=4402)
    for d,r in [(doc,'doctor'),(doc2,'otherDoctor')]:
        api('PUT',f"/admin/doctors/{d['id']}/account",{'userId':actors[r]['id']},admin)
    api('PUT',f"/admin/doctors/{doc['id']}/account",{'userId':actors['doctor']['id']},admin,409,4501)
    doctor=login(actors['doctor']); other_doctor=login(actors['otherDoctor'])
    check('管理目录、重复科室、被引用删除保护、医生绑定与重复绑定拒绝')
    schedules=api('GET',f"/doctors/{doc['id']}/schedules",token=user)
    check('用户医院科室医生目录及三天十二场次',len(schedules)==12 and len({s['serverTime'] for s in schedules})==1)
    api('PATCH',f"/patients/{patients[0]['id']}/status",{'enabled':False},user)
    book('disabled',expected=409,code=4102)
    api('PATCH',f"/patients/{patients[0]['id']}/status",{'enabled':True},user)
    api('PUT','/admin/booking-settings',{'capacityPerSession':6,'registrationFee':'12.34'},admin)
    api('PUT','/admin/booking-settings',{'capacityPerSession':0,'registrationFee':'0'},admin,400,1001)
    key=str(uuid.uuid4())
    main=book('completed',key=key)
    replay=book('replay',key=key,expected=200)
    check('停用限制、重新启用、预约暂占及幂等重放',replay['appointmentOrderId']==main['appointmentOrderId'] and replay['replayed'])
    book('conflict',p=patients[1],key=key,expected=409,code=4103)
    book('duplicate',expected=409,code=4204)
    book('outOfRange',day=3,expected=409,code=4202)
    api('GET','/appointments/'+main['appointmentOrderId'],token=other,expected=403,code=2002)
    api('POST','/payments/'+main['paymentOrderId']+'/attempts',{'channel':'MOCK'},other,403,2002)
    check('重复有效预约、幂等内容冲突、三天边界和预约支付归属')
    a=attempt(main); notify(a,'fail')
    failed=wait(main,lambda v:v['paymentStatus']=='UNPAID')
    a=attempt(main); notification=notify(a)
    confirmed=wait(main,lambda v:v['status']=='CONFIRMED')
    duplicate=api('POST','/payment-notifications/MOCK',notification)
    invalid={**notification,'signature':'invalid'}
    api('POST','/payment-notifications/MOCK',invalid,expected=400,code=4302)
    check('支付失败重试、异步确认、通知幂等和验签',confirmed['paymentAttemptCount']==2 and duplicate['duplicate'] and confirmed['visitNumber']==1)
    api('PATCH','/patients/'+patients[0]['id'],{'name':'修改后就诊人'},user)
    api('PATCH','/admin/doctors/'+doc['id'],{'title':'修改后职称'},admin)
    snap=detail(main)
    check('就诊人与医生资料修改不改写预约凭证',snap['patient']['name']==patients[0]['name'] and snap['doctor']['title']=='主任医师')
    api('POST','/doctor/appointments/'+main['appointmentOrderId']+'/start-visit',token=doctor,expected=409,code=4502)
    api('GET','/doctor/appointments/'+main['appointmentOrderId'],token=other_doctor,expected=404,code=4001)
    api('POST','/doctor/appointments/'+main['appointmentOrderId']+'/start-visit',token=other_doctor,expected=403,code=2002)
    clock('13:29:00')
    api('POST','/appointments/'+main['appointmentOrderId']+'/check-in',token=user,expected=409,code=4503)
    clock('13:30:00')
    api('POST','/appointments/'+main['appointmentOrderId']+'/check-in',token=user)
    api('POST','/appointments/'+main['appointmentOrderId']+'/check-in',token=user,expected=409,code=4502)
    api('POST','/doctor/appointments/'+main['appointmentOrderId']+'/complete-visit',token=doctor,expected=409,code=4502)
    api('POST','/doctor/appointments/'+main['appointmentOrderId']+'/start-visit',token=doctor)
    api('POST','/doctor/appointments/'+main['appointmentOrderId']+'/start-visit',token=doctor,expected=409,code=4502)
    api('POST','/doctor/appointments/'+main['appointmentOrderId']+'/complete-visit',token=doctor)
    api('POST','/doctor/appointments/'+main['appointmentOrderId']+'/complete-visit',token=doctor,expected=409,code=4502)
    ud=detail(main); dd=api('GET','/doctor/appointments/'+main['appointmentOrderId'],token=doctor)
    check('签到开放边界、接诊顺序、重复动作及用户医生双端一致',all(ud['visit'][k]==dd['visit'][k] for k in ('status','checkedInAt','startedAt','completedAt')))
    future=book('refunded',day=2)
    paid=pay(future); cancel(future,202)
    refunded=wait(future,lambda v:v['paymentStatus']=='REFUNDED')
    cancel(future)
    next_order=book('rebooked',day=2); rebooked=pay(next_order)
    check('提前取消、异步全额退款、重复取消及退号不复用',refunded['refund']['completedAt'] and rebooked['visitNumber']==paid['visitNumber']+1)
    # 正好24小时取消边界：后天14点，推进到明天14点。
    api('PUT','/test-control/clock',{'instant':str(DAY+dt.timedelta(days=1))+'T14:00:00+08:00'},admin)
    api('POST','/appointments/'+next_order['appointmentOrderId']+'/cancel',token=user,expected=409,code=4205)
    clock('13:35:00')
    cancel(next_order,202); wait(next_order,lambda v:v['paymentStatus']=='REFUNDED')
    unpaid=book('unpaidCancelled',day=1); cancel(unpaid); cancel(unpaid)
    check('24小时取消边界、未支付取消和重复取消')
    expired=book('expired',day=1,session='MORNING_FIRST')
    late=book('lateRefunded',day=1,session='MORNING_SECOND'); late_attempt=attempt(late)
    clock('13:51:00')
    api('POST','/payments/'+expired['paymentOrderId']+'/attempts',{'channel':'MOCK'},user,409,4004)
    notify(late_attempt)
    wait(late,lambda v:v['status']=='EXPIRED' and v['paymentStatus']=='REFUNDED')
    wait(expired,lambda v:v['status']=='EXPIRED' and v['paymentStatus']=='CLOSED')
    check('待支付自动过期、截止后拒付与迟到支付原路退款')
    clock('13:44:00')
    cutoff=book('paidAfterBookingCutoff',p=patients[1]); clock('13:45:00')
    book('cutoffRejected',p=patients[2],expected=409,code=4202)
    pay(cutoff)
    check('下单截止与支付截止分离：截止后旧单仍可支付')
    clock('13:35:00')
    api('PUT','/admin/booking-settings',{'capacityPerSession':2,'registrationFee':'15.00'},admin)
    current=api('GET',f"/doctors/{doc['id']}/schedules",token=user)
    existing=next(s for s in current if s['appointmentDate']==str(DAY) and s['sessionType']=='AFTERNOON_FIRST')
    fresh=next(s for s in current if s['appointmentDate']==str(DAY+dt.timedelta(days=2)) and s['sessionType']=='AFTERNOON_SECOND')
    check('统一设置只影响首次生成场次',existing['totalCapacity']==6 and existing['registrationFee']=='12.34' and fresh['totalCapacity']==2 and fresh['registrationFee']=='15.00')
    def compete(p):
        body={'doctorId':doc['id'],'patientId':p['id'],'appointmentDate':str(DAY+dt.timedelta(days=2)),'sessionType':'AFTERNOON_SECOND'}
        req=urllib.request.Request('http://127.0.0.1:8081/api/v1/appointments',data=json.dumps(body).encode(),method='POST',headers={'Content-Type':'application/json','Authorization':'Bearer '+user,'Idempotency-Key':str(uuid.uuid4())})
        try:
            with opener.open(req,timeout=15) as r: return r.status,json.load(r)
        except urllib.error.HTTPError as e: return e.code,json.load(e)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool: competition=list(pool.map(compete,patients[2:5]))
    records['competition']=clean(competition)
    check('三人并发抢两个名额：仅两单成功且一单号满',sorted(s for s,_ in competition)==[201,201,409] and [b['code'] for s,b in competition if s==409]==[4002])
    for n,(s,b) in enumerate(competition):
        if s==201:
            o=b['data'];records['competition'+str(n)]=o;cancel(o)
    api('PUT','/admin/booking-settings',{'capacityPerSession':6,'registrationFee':'12.34'},admin)
    # 保留同一场次四种就诊状态供手动查看。
    waiting=book('waiting',p=patients[2]); pay(waiting)
    progress=book('inProgress',p=patients[3]);pay(progress)
    for o in (waiting,progress): api('POST','/appointments/'+o['appointmentOrderId']+'/check-in',token=user)
    api('POST','/doctor/appointments/'+progress['appointmentOrderId']+'/start-visit',token=doctor)
    clock('16:00:00')
    api('POST','/appointments/'+cutoff['appointmentOrderId']+'/check-in',token=user,expected=409,code=4504)
    check('场次结束后首次签到拒绝且预约保持已确认',detail(cutoff)['status']=='CONFIRMED' and detail(cutoff)['visit']['status']=='NOT_CHECKED_IN')
    # 已签到记录允许跨日接诊：第二日开始，再回到当天供体验。
    cross=book('crossDay',p=patients[4],day=1,session='AFTERNOON_FIRST');pay(cross)
    api('PUT','/test-control/clock',{'instant':str(DAY+dt.timedelta(days=1))+'T13:30:00+08:00'},admin)
    api('POST','/appointments/'+cross['appointmentOrderId']+'/check-in',token=user)
    api('PUT','/test-control/clock',{'instant':str(DAY+dt.timedelta(days=2))+'T08:00:00+08:00'},admin)
    api('POST','/doctor/appointments/'+cross['appointmentOrderId']+'/start-visit',token=doctor)
    api('POST','/doctor/appointments/'+cross['appointmentOrderId']+'/complete-visit',token=doctor)
    check('已签到预约可跨日开始和完成接诊')
    clock('13:35:00')
    rows=api('GET',f'/doctor/appointments?date={DAY}&sessionType=AFTERNOON_FIRST',token=doctor)
    overview=api('GET',f'/doctor/sessions?date={DAY}',token=doctor)
    first=next(s for s in overview if s['sessionType']=='AFTERNOON_FIRST')
    check('医生四状态名单、序号排序和人数统计', [r['visitNumber'] for r in rows]==sorted(r['visitNumber'] for r in rows) and [first[k] for k in ('notCheckedInCount','waitingCount','inProgressCount','completedCount')]==[1,1,1,1])
    asc=api('GET','/appointments/my?order=ASC',token=user); desc=api('GET','/appointments/my?order=DESC',token=user)
    check('我的预约包含历史终态且支持正反排序',[v['id'] for v in asc]==list(reversed([v['id'] for v in desc])))
    private=ROOT/'.local-env'/'workflow-actors.json'
    private.write_text(json.dumps({'run':RUN,'date':str(DAY),'actors':actors,'records':records,'documentNo':document(731)})); private.chmod(0o600)
    check('完整业务主流程与分支验收完成')
finally:
    api('PUT','/admin/booking-settings',{'capacityPerSession':original_settings['capacityPerSession'],'registrationFee':original_settings['registrationFee']},admin)
    api('DELETE','/test-control/clock',token=admin)
    save()
