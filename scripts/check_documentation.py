#!/usr/bin/env python3
"""只读核对当前源码契约与文档；不启动服务、不修改数据库，不替代业务测试。"""
from pathlib import Path
import hashlib
import json
import os
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)

def normalized(path):
    return re.sub(r'/\d+(?=/|$)', '/{}', re.sub(r'\{[^}]*\}', '{}', path.split('?')[0].rstrip('/')))

routes = set()
for p in Path('src/main/java').rglob('*.java'):
    source = p.read_text()
    if not re.search(r'@RestController\b', source):
        continue
    prefix = re.search(r'@RequestMapping\("([^"]+)"\)', source)
    prefix = prefix[1] if prefix else ''
    for method, suffix in re.findall(r'@(Get|Post|Put|Patch|Delete)Mapping(?:\("([^"]*)"\))?', source):
        routes.add((method.upper(), normalized(prefix + suffix)))
api = Path('docs/api.md').read_text() + '\n' + Path('docs/visit/api.md').read_text()
documented = {(method, normalized(path)) for method, path in re.findall(r'\b(GET|POST|PUT|PATCH|DELETE)\s+(/api/v1/[^\s`|]*)', api)}
assert routes <= documented, ('未记录的实际接口', sorted(routes - documented))
# 删除的路由只允许出现在明确说明已删除的段落中。
retired = {('POST', '/api/v1/payments/{}/simulate-success')}
assert documented - routes <= retired, ('文档声明了未知接口', sorted(documented - routes))

response = Path('src/main/java/com/qsx/appoint/appointment/dto/AppointmentResponse.java').read_text()
fields = re.findall(r'private final [\w<>]+ (\w+);', response)
line = re.search(r'详情和列表元素字段为：(.*?)。', api)[1]
assert set(fields) == set(re.findall(r'`(\w+)`', line)), ('预约DTO字段不匹配', fields)
schedule = Path('src/main/java/com/qsx/appoint/catalog/dto/ScheduleResponse.java').read_text()
public_fields = set(re.findall(r'private final [\w<>]+ (\w+);', schedule)) - {'id'}
catalog_section = api[api.index('## 目录与场次'):api.index('## 管理员医院')]
sample = json.loads(re.search(r'```json\s*(.*?)\s*```', catalog_section, re.S)[1])
assert set(sample) == public_fields, ('场次DTO字段不匹配', set(sample), public_fields)

def record_fields(source, name):
    match = re.search(r'public record '+name+r'\s*\((.*?)\)\s*\{', source, re.S)
    assert match, name
    return [part.strip().split()[-1] for part in match[1].split(',')]
visit_design = Path('docs/visit/design.md').read_text()
visit_response = Path('src/main/java/com/qsx/appoint/appointment/dto/AppointmentVisitResponse.java').read_text()
assert record_fields(visit_response, 'AppointmentVisitResponse') == record_fields(visit_design, 'AppointmentVisitResponse')
record_names = ['DoctorSessionResponse', 'DoctorAppointmentRowResponse', 'DoctorVisitProgressResponse', 'DoctorPatientResponse', 'DoctorAppointmentDetailResponse']
for name in record_names:
    source = Path('src/main/java/com/qsx/appoint/bookingquery/dto') / (name + '.java')
    assert record_fields(source.read_text(), name) == record_fields(visit_design, name), name

markdown = subprocess.check_output(['rg', '--files', '--hidden', '-g', '*.md', '-g', '!.git/**', '-g', '!target/**'], text=True).splitlines()
errors = []
link_count = 0
for name in markdown:
    p = Path(name)
    text = p.read_text()
    if text.count('```') % 2:
        errors.append((name, '未闭合代码围栏'))
    for target in re.findall(r'\]\(([^)]+)\)', text):
        if '://' in target or target.startswith('mailto:'):
            continue
        file, _, anchor = target.partition('#')
        destination = p.parent / file if file else p
        link_count += 1
        if not destination.exists():
            errors.append((name, '失效链接', target))
            continue
        if anchor and destination.suffix == '.md':
            headings = re.findall(r'^#{1,6}\s+(.+?)\s*#*$', destination.read_text(), re.M)
            slugs = [re.sub(r'\s+', '-', re.sub(r'[^\w\s-]', '', h.lower())) for h in headings]
            if anchor not in slugs:
                errors.append((name, '失效锚点', target))
assert not errors, json.dumps(errors, ensure_ascii=False, indent=2)

json_samples = 0
for p in [Path('docs/api.md'), *Path('docs/visit').glob('*.md')]:
    for block in re.findall(r'```json\s*(.*?)\s*```', p.read_text(), re.S):
        json.loads(block)
        json_samples += 1
http_requests = 0
http_json = 0
for p in Path('http').glob('*.http'):
    text = p.read_text()
    http_requests += len(re.findall(r'^(?:GET|POST|PUT|PATCH|DELETE)\s+', text, re.M))
    for match in re.finditer(r'^\{', text, re.M):
        json.JSONDecoder().raw_decode(text[match.start():])
        http_json += 1
    assert not re.search(r'^@\w*[Dd]ate\s*=\s*\d{4}-\d{2}-\d{2}', text, re.M), ('固定运行日期', str(p))
json.loads(Path('http/http-client.env.json').read_text())

# Current visit contracts are checked independently of historical test evidence.
window_fields = {'checkInOpensAt', 'checkInClosesAt'}
assert window_fields <= set(record_fields(visit_response, 'AppointmentVisitResponse'))
for field in window_fields:
    assert f'`visit.{field}`' in api, ('签到窗口API字段缺失', field)
error_codes = Path('src/main/java/com/qsx/appoint/common/ErrorCode.java').read_text()
for number, message in [(4503, '尚未到签到开放时间'), (4504, '已超过预约场次签到截止时间')]:
    assert re.search(r'\(' + str(number) + r', "' + message + r'", HttpStatus.CONFLICT\)', error_codes), number
    assert str(number) in api, ('签到窗口错误码未记录', number)

# Archive completeness is verified against the actual Git tree, not just entry count.
manifest = json.loads(Path('.scratch/archive-manifest.json').read_text())
checkpoint = manifest['baselineCommit']
tracked = set(subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', checkpoint, '--', '.scratch'], text=True).splitlines())
entries = manifest['entries']
assert len(entries) == len({e['source'] for e in entries}), '重复原文件'
assert tracked == {e['source'] for e in entries}, '归档来源与Git树不一致'
for entry in entries:
    original = subprocess.check_output(['git', 'show', checkpoint + ':' + entry['source']])
    assert hashlib.sha256(original).hexdigest() == entry['sourceSha256'], entry['source']
    target, _, anchor = entry['destination'].partition('#')
    assert Path(target).is_file(), target
    if anchor:
        assert '## ' + anchor in Path(target).read_text(), (target, anchor)
    if entry['action'] == 'move' and not target.endswith('.md'):
        assert hashlib.sha256(Path(target).read_bytes()).hexdigest() == entry['sourceSha256'], target
    for extra in entry.get('additionalDestinations', []):
        assert Path(extra).is_file(), extra
    assert not Path(entry['source']).exists(), ('原任务正文仍存在', entry['source'])

archive = Path('.scratch/archive/delivery-quality/evidence/test-suite-refactor')
source_manifest = (archive/'code-manifest.sha256').read_text().splitlines()
for row in source_manifest:
    digest, name = row.split('  ', 1)
    original = subprocess.check_output(['git', 'show', '9c5878b:' + name])
    assert hashlib.sha256(original).hexdigest() == digest, ('历史被测源码摘要不匹配', name)
counts = json.loads((archive/'final-counts.json').read_text())
for kind in ['unit', 'mysql', 'reliability']:
    suites = json.loads((archive/f'final-{kind}-suites.json').read_text())
    assert sum(int(x['tests']) for x in suites) == counts['final'][kind], kind
    assert all(int(x[k]) == 0 for x in suites for k in ['failures', 'errors', 'skipped']), kind
requests = json.loads((archive/'curl-report.json').read_text())['requests']
assert len(requests) == counts['final']['httpRequests']
assert all(x['status'] == x['expectedStatus'] for x in requests)

# New acceptance is separate evidence. Future source changes are reported as drift,
# not relabeled as tested or forced to match an old delivery's working tree.
visit_evidence = Path('.scratch/visit-checkin-window/verification')
visit_acceptance = None
if (visit_evidence/'final-counts.json').exists():
    visit_counts = json.loads((visit_evidence/'final-counts.json').read_text())
    for kind in ['focused', 'unit', 'mysql', 'reliability']:
        suites = json.loads((visit_evidence/f'final-{kind}-suites.json').read_text())
        assert sum(int(x['tests']) for x in suites) == visit_counts[kind], kind
        assert suites and all(int(x[k]) == 0 for x in suites for k in ['failures', 'errors', 'skipped']), kind
        assert all(len(x['cases']) == int(x['tests']) and all(c['result'] == 'passed' for c in x['cases']) for x in suites), kind
        suffix = {'mysql': 'MySqlIT', 'reliability': 'ReliabilityIT'}.get(kind)
        assert suffix is None or all(x['name'].endswith(suffix) for x in suites), ('混入其他profile旧报告', kind)
    visit_requests = json.loads((visit_evidence/'curl-report.json').read_text())['requests']
    assert len(visit_requests) == visit_counts['httpRequests']
    assert all(x['status'] == x['expectedStatus'] for x in visit_requests)
    for item in visit_requests:
        data = item['response'].get('data')
        if item['method'] == 'GET' and item['path'].startswith('/api/v1/appointments') and item['status'] == 200:
            rows = data if isinstance(data, list) else [data]
            for row in rows:
                if row['status'] == 'CONFIRMED':
                    assert window_fields <= row['visit'].keys(), item['path']
                else:
                    assert row['visit'] is None, item['path']
    current_manifest = (visit_evidence/'code-manifest.sha256').read_text().splitlines()
    assert len(current_manifest) == visit_counts['sourceFiles']
    assert hashlib.sha256((visit_evidence/'code-manifest.sha256').read_bytes()).hexdigest() == visit_counts['sourceManifestSha256']
    recorded_names = set()
    matches = True
    for row in current_manifest:
        digest, name = row.split('  ', 1)
        assert name not in recorded_names, ('重复被测源码', name)
        recorded_names.add(name)
        matches = matches and Path(name).is_file() and hashlib.sha256(Path(name).read_bytes()).hexdigest() == digest
    current_names = {str(p) for root in ['src', 'scripts', '.mvn'] for p in Path(root).rglob('*')
                     if p.is_file() and '__pycache__' not in p.parts and p.suffix != '.pyc' and p.name != 'timing.properties'} | {'pom.xml', 'mvnw', 'mvnw.cmd', 'compose.yaml', '.env.dev.example', '.env.test.example', '.env.demo.example', 'compose.env.example'}
    matches = matches and recorded_names == current_names
    visit_acceptance = {'counts': visit_counts, 'sourceMatchesWorkingTree': matches}

# Current test class inventory and explicit Class.method references.
test_files = list(Path('src/test/java').rglob('*.java'))
tests = {p.stem:p.read_text() for p in test_files if p.name.endswith(('Test.java','MySqlIT.java','ReliabilityIT.java','RedisIT.java')) and '@interface ' not in p.read_text()}
inventory = Path('docs/testing/inventory.md').read_text()
listed = re.findall(r'^\| `([A-Za-z0-9]+)` \|', inventory, re.M)
assert set(listed) == set(tests), ('测试清单不匹配', sorted(set(tests)-set(listed)), sorted(set(listed)-set(tests)))
coverage = Path('docs/testing/scenario-coverage.md').read_text()
for namespace, maximum in [('messaging',32), ('booking-time',18)]:
    ids = re.findall(r'^\| '+namespace+r'/S(\d+) \|', coverage, re.M)
    assert len(ids) == maximum and set(map(int,ids)) == set(range(1,maximum+1)), namespace
window_ids = re.findall(r'^\| checkin-window/W(\d+) \|', coverage, re.M)
assert len(window_ids) == 12 and set(map(int, window_ids)) == set(range(1, 13)), '签到窗口场景映射不完整'
method_refs = re.findall(r'\b([A-Z][A-Za-z0-9_]*(?:Test|MySqlIT|ReliabilityIT|RedisIT))\.([a-z][A-Za-z0-9_]+)', coverage)
for cls, method in method_refs:
    assert cls in tests and re.search(r'\b'+method+r'\s*\(', tests[cls]), ('测试方法不存在',cls,method)
for cls in set(re.findall(r'\b([A-Z]\w*(?:Test|MySqlIT|ReliabilityIT|RedisIT))\b', coverage)):
    assert cls in tests, ('测试类不存在',cls)
# Active documents may mention archive evidence, but cannot rely on removed specs.
for p in [Path('README.md'), *Path('docs').rglob('*.md')]:
    text = p.read_text()
    for entry in entries:
        if entry['source'] in text:
            assert p.as_posix() == 'docs/agents/issue-tracker.md', ('现行文档残留旧路径',str(p),entry['source'])
print(json.dumps({'markdownFiles':len(markdown),'localLinksAndAnchors':link_count,'documentedRestOperations':len(routes),'appointmentDtoFields':len(fields),'scheduleDtoFields':len(public_fields),'doctorRecordContracts':len(record_names),'appointmentVisitRecordContracts':1,'jsonExamples':json_samples,'httpRequests':http_requests,'httpJsonBodies':http_json,'archiveSources':len(entries),'historicalSourceCheckpoint':'9c5878b','historicalSourceManifestFiles':len(source_manifest),'visitWindowAcceptance':visit_acceptance,'currentTestClasses':len(tests),'explicitTestMethodReferences':len(method_refs),'historicalCounts':counts['final'],'businessTestsRerun':False}, ensure_ascii=False, indent=2))
