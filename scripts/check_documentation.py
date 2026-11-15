#!/usr/bin/env python3
"""只读核对当前文档链接、示例与API契约，不运行业务测试。"""
import json
import os
import re
import subprocess
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)


def normalized(path):
    path = path.split('?')[0].rstrip('/')
    return re.sub(r'/\d+(?=/|$)', '/{}', re.sub(r'\{[^}]*\}', '{}', path))


def record_fields(path, name):
    source = path.read_text()
    match = re.search(r'public record ' + name + r'\s*\((.*?)\)\s*\{', source, re.S)
    assert match, ('未找到record', name)
    return {part.strip().split()[-1] for part in match[1].split(',')}


markdown = subprocess.check_output(
    ['rg', '--files', '--hidden', '-g', '*.md', '-g', '!.git/**', '-g', '!target/**'],
    text=True).splitlines()
errors, links, json_samples = [], 0, 0
for name in markdown:
    path = Path(name)
    text = path.read_text()
    if text.count('```') % 2:
        errors.append((name, '未闭合代码围栏'))
    for target in re.findall(r'\]\(([^)]+)\)', text):
        if '://' in target or target.startswith('mailto:'):
            continue
        file, _, anchor = unquote(target).partition('#')
        destination = path.parent / file if file else path
        links += 1
        if not destination.exists():
            errors.append((name, '失效链接', target))
        elif anchor and destination.suffix == '.md':
            headings = re.findall(r'^#{1,6}\s+(.+?)\s*#*$', destination.read_text(), re.M)
            slugs = {re.sub(r'\s+', '-', re.sub(r'[^\w\s-]', '', h.lower())) for h in headings}
            if anchor not in slugs:
                errors.append((name, '失效锚点', target))
    for block in re.findall(r'```json\s*(.*?)\s*```', text, re.S):
        json.loads(block)
        json_samples += 1
assert not errors, json.dumps(errors, ensure_ascii=False, indent=2)

api = Path('docs/api.md').read_text()
routes = set()
for path in Path('src/main/java').rglob('*.java'):
    source = path.read_text()
    if not re.search(r'@RestController\b', source):
        continue
    prefix = re.search(r'@RequestMapping\("([^"]+)"\)', source)
    prefix = prefix[1] if prefix else ''
    for method, suffix in re.findall(r'@(Get|Post|Put|Patch|Delete)Mapping(?:\("([^"]*)"\))?', source):
        routes.add((method.upper(), normalized(prefix + suffix)))
documented = {(method, normalized(path)) for method, path in
              re.findall(r'\b(GET|POST|PUT|PATCH|DELETE)\s+(/api/v1/[^\s`|]*)', api)}
assert routes == documented, ('接口文档不匹配', sorted(routes - documented), sorted(documented - routes))

response = Path('src/main/java/com/qsx/appoint/appointment/dto/AppointmentResponse.java').read_text()
fields = set(re.findall(r'private final [\w<>]+ (\w+);', response))
line = re.search(r'详情和列表元素字段为：(.*?)。', api)[1]
assert fields == set(re.findall(r'`(\w+)`', line)), '预约DTO字段不匹配'
schedule = Path('src/main/java/com/qsx/appoint/catalog/dto/ScheduleResponse.java').read_text()
schedule_fields = set(re.findall(r'private final [\w<>]+ (\w+);', schedule)) - {'id'}
section = api[api.index('## 目录与场次'):api.index('## 管理员医院')]
sample = json.loads(re.search(r'```json\s*(.*?)\s*```', section, re.S)[1])
assert set(sample) == schedule_fields, '场次DTO字段不匹配'

contracts = [
    ('用户就诊进度', 'appointment/dto', 'AppointmentVisitResponse'),
    ('医生场次概览', 'bookingquery/dto', 'DoctorSessionResponse'),
    ('医生人员名单项', 'bookingquery/dto', 'DoctorAppointmentRowResponse'),
    ('医生只读详情', 'bookingquery/dto', 'DoctorAppointmentDetailResponse'),
    ('医生详情就诊人', 'bookingquery/dto', 'DoctorPatientResponse'),
    ('医生详情就诊进度', 'bookingquery/dto', 'DoctorVisitProgressResponse'),
]
for label, folder, name in contracts:
    row = re.search(r'^\| ' + label + r' \| (.*?) \|$', api, re.M)
    assert row, ('缺少响应字段说明', label)
    path = Path('src/main/java/com/qsx/appoint') / folder / (name + '.java')
    assert record_fields(path, name) == set(re.findall(r'`(\w+)`', row[1])), ('响应字段不匹配', name)

print(json.dumps({'markdownFiles': len(markdown), 'localLinksAndAnchors': links,
    'documentedRestOperations': len(routes), 'appointmentDtoFields': len(fields),
    'scheduleDtoFields': len(schedule_fields), 'visitRecordContracts': len(contracts),
    'jsonExamples': json_samples,
    'businessTestsRerun': False}, ensure_ascii=False, indent=2))
