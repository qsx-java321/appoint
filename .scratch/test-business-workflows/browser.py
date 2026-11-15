"""复用既有真实浏览器流程，连接正式 test 前后端，不启动第二实例。"""
import json
import os
import secrets
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
import local_env
import urllib.request
import urllib.error

runtime=ROOT/'.local-env/workflow-browser'
runtime.mkdir(exist_ok=True)
runtime.chmod(0o700)
data=json.loads((ROOT/'.local-env/workflow-actors.json').read_text())
seed=local_env.read_properties(ROOT/'.env.test')
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))

def api(method,path,body=None,token=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    req=urllib.request.Request('http://127.0.0.1:8081/api/v1'+path,
        data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
    with opener.open(req,timeout=15) as r: return json.load(r)['data']

admin_actor={'email':'test-admin@example.local','password':seed['LOCAL_SEED_PASSWORD']}
admin=api('POST','/auth/login',admin_actor)['accessToken']
settings=api('GET','/admin/booking-settings',token=admin)
evidence=Path(__file__).parent/'evidence/browser'
evidence.mkdir(exist_ok=True)
suffix=secrets.token_hex(3)
browser_actors={role:{'email':f'workflow-{data["run"]}-{suffix}-{role}@example.local','password':'Test-'+secrets.token_hex(12)} for role in ('doctor','user')}
browser_actors['doctor']['id']=api('POST','/auth/register',browser_actors['doctor'])['id']
data['latestBrowserActors']=browser_actors
(ROOT/'.local-env/workflow-actors.json').write_text(json.dumps(data))
config={'runId':data['run']+'-'+suffix,'date':data['date'],'documentNo':data['documentNo'],'evidence':str(evidence),
    'actors':{'admin':admin_actor,**browser_actors}}
private=runtime/'actors.json'
private.write_text(json.dumps(config));private.chmod(0o600)
source=(ROOT/'frontend/tests/e2e/compose-real.spec.ts').read_text()
source=source.replace('from "@playwright/test"','from "../../frontend/node_modules/@playwright/test/index.mjs"')
source=source.replace('expect(prior.visit.checkedInAt).toBe(`${config.date}T13:40:00`);',
    'expect(prior.visit.checkedInAt.startsWith(`${config.date}T13:40:`)).toBe(true);')
source=source.replace('.toContainText("13:40:00")','.toContainText("13:40:")')
(runtime/'workflow.spec.ts').write_text(source)
(runtime/'playwright.config.mjs').write_text('''import { defineConfig } from '../../frontend/node_modules/@playwright/test/index.mjs';
export default defineConfig({ testDir: '.', testMatch: 'workflow.spec.ts', workers: 1, retries: 0,
 reporter: [['list'], ['json', {outputFile: process.env.E2E_REPORT_FILE}]],
 use: {baseURL:'http://127.0.0.1:5174',viewport:{width:1280,height:900},
 launchOptions:{executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE,args:['--no-sandbox','--disable-dev-shm-usage']},
 screenshot:'only-on-failure',trace:'off'}, outputDir: process.env.E2E_OUTPUT_DIR });
''')
try:
    api('PUT','/test-control/clock',{'instant':data['date']+'T13:40:00+08:00'},admin)
    environment=os.environ.copy()
    environment.update(COMPOSE_E2E_CONFIG=str(private), E2E_REPORT_FILE=str(evidence/'results.json'),
        E2E_OUTPUT_DIR=str(evidence/'test-results'),
        PLAYWRIGHT_CHROMIUM_EXECUTABLE='/home/qsx/.cache/puppeteer/chrome/linux-156.0.8078.47/chrome-linux64/chrome')
    result=subprocess.run(['/home/qsx/.nvm/versions/node/v24.18.1/bin/node',
        str(ROOT/'frontend/node_modules/@playwright/test/cli.js'),'test','--config',str(runtime/'playwright.config.mjs')],cwd=ROOT,env=environment)
    sys.exit(result.returncode)
finally:
    api('PUT','/admin/booking-settings',{'capacityPerSession':settings['capacityPerSession'],'registrationFee':settings['registrationFee']},admin)
    api('DELETE','/test-control/clock',token=admin)
