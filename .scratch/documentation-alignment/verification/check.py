#!/usr/bin/env python3
"""只读核对当前源码契约与文档；不启动服务、不修改数据库，不替代业务测试。"""
from pathlib import Path
import collections
import hashlib
import json
import os
import re
import subprocess

ROOT = Path(__file__).resolve().parents[3]
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
assert len(routes) == 38, len(routes)
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

for folder in Path('.scratch').iterdir():
    if not folder.is_dir() or folder.name == 'documentation-alignment':
        continue
    issues = list((folder / 'issues').glob('*.md'))
    if issues and all(re.search(r'^Status: resolved$', p.read_text(), re.M) for p in issues):
        for p in [folder/'spec.md', folder/'map.md']:
            if p.exists():
                state = re.search(r'^Status: (.+)$', p.read_text(), re.M)
                if state:
                    assert state[1] == 'resolved', ('完成子任务与总状态不符', str(p), state[1])
for row in Path('.scratch/test-suite-refactor/verification/code-manifest.sha256').read_text().splitlines():
    digest, name = row.split('  ', 1)
    assert hashlib.sha256(Path(name).read_bytes()).hexdigest() == digest, ('实现或测试发生变化', name)
counts = json.loads(Path('.scratch/test-suite-refactor/verification/final-counts.json').read_text())
assert counts['final']['unit'] == 340 and counts['final']['mysql'] == 38 and counts['final']['reliability'] == 16
print(json.dumps({'markdownFiles':len(markdown),'localLinksAndAnchors':link_count,'documentedRestOperations':len(routes),'appointmentDtoFields':len(fields),'scheduleDtoFields':len(public_fields),'doctorRecordContracts':len(record_names),'appointmentVisitRecordContracts':1,'jsonExamples':json_samples,'httpRequests':http_requests,'httpJsonBodies':http_json,'unchangedSourceManifestFiles':360,'businessTestsRerun':False}, ensure_ascii=False, indent=2))
