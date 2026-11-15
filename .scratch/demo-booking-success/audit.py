"""只读核对本轮 demo 成功预约和环境隔离。"""
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import local_env

out = Path(__file__).parent / 'evidence'
result = json.loads((out / 'result.json').read_text())
baseline = json.loads((out / 'baseline.json').read_text())
assert result['passed']
appointment = int(result['appointmentId'])
user = int(result['userId'])
patient = int(result['patientId'])
payment = int(result['paymentId'])

def sql(query):
    return local_env.mysql('SET NAMES utf8mb4; USE appoint_demo; ' + query)

schedule = int(sql(f'SELECT schedule_id FROM appointment_orders WHERE id={appointment};'))
audit = {
    'appointment': sql(f'SELECT a.id,a.status,p.status,a.visit_number,a.appointment_date,a.start_time,a.end_time FROM appointment_orders a JOIN payment_orders p ON p.appointment_order_id=a.id WHERE a.id={appointment};'),
    'inventory': sql(f'SELECT total_capacity,available_capacity,held_capacity,confirmed_capacity,next_visit_number FROM schedules WHERE id={schedule};'),
    'outbox': sql(f'SELECT event_type,status,COUNT(*) FROM payment_outbox_events WHERE aggregate_id={payment} GROUP BY event_type,status;'),
    'settings': sql('SELECT capacity_per_session,registration_fee,currency FROM booking_settings WHERE id=1;'),
    'counts': sql("SELECT 'users',COUNT(*) FROM users UNION ALL SELECT 'patients',COUNT(*) FROM patients UNION ALL SELECT 'appointments',COUNT(*) FROM appointment_orders UNION ALL SELECT 'payments',COUNT(*) FROM payment_orders;"),
    'checks': {
        'confirmedPaidAndOwned': sql(f"SELECT COUNT(*) FROM appointment_orders a JOIN payment_orders p ON p.appointment_order_id=a.id WHERE a.id={appointment} AND a.user_id={user} AND a.patient_id={patient} AND a.status='CONFIRMED' AND p.status='PAID' AND a.visit_number>0;"),
        'activeClaim': sql(f'SELECT COUNT(*) FROM appointment_active_claims WHERE appointment_order_id={appointment};'),
        'noVisitRecord': sql(f'SELECT COUNT(*) FROM visit_records WHERE appointment_order_id={appointment};'),
        'correctInventory': sql(f'SELECT COUNT(*) FROM schedules WHERE id={schedule} AND total_capacity=available_capacity+held_capacity+confirmed_capacity AND available_capacity={int(result["selected"]["availableCapacity"])-1} AND held_capacity=0 AND confirmed_capacity=1;'),
        'publishedPaymentEvent': sql(f"SELECT COUNT(*) FROM payment_outbox_events WHERE aggregate_id={payment} AND event_type='PaymentSucceeded' AND status='PUBLISHED';"),
        'processedPaymentEvent': sql(f"SELECT COUNT(*) FROM coordination_inbox_events i JOIN payment_outbox_events o ON o.event_id=i.event_id WHERE o.aggregate_id={payment} AND o.event_type='PaymentSucceeded';"),
        'noRefund': sql(f'SELECT COUNT(*) FROM payment_refunds WHERE payment_order_id={payment};'),
    },
    'processes': {name: local_env.status(name) for name in ('dev', 'test', 'demo')},
}
expected = {'confirmedPaidAndOwned': '1', 'activeClaim': '1', 'noVisitRecord': '0', 'correctInventory': '1',
            'publishedPaymentEvent': '1', 'processedPaymentEvent': '1', 'noRefund': '0'}
assert audit['checks'] == expected, audit['checks']
catalog = sql('SELECT h.id,h.name,d.id,d.name,dr.id,dr.doctor_no,dr.name FROM hospitals h JOIN departments d ON d.hospital_id=h.id JOIN doctors dr ON dr.department_id=d.id ORDER BY h.id,d.id,dr.id;')
assert catalog == baseline['catalog']
audit['originalCatalogUnchanged'] = True
assert audit['counts'] == 'users\t4\npatients\t1\nappointments\t1\npayments\t1'
assert audit['processes']['demo'] and not audit['processes']['test'] and not audit['processes']['dev']
isolation = json.loads((out / 'isolation-before.json').read_text())
after = {db: {name: hashlib.sha256(local_env.mysql('USE ' + db + '; ' + query).encode()).hexdigest()
              for name, query in isolation['queries'].items()} for db in ('appoint', 'appoint_test')}
assert after == isolation['fingerprints'], 'dev/test关键业务记录发生变化'
(out / 'isolation-after.json').write_text(json.dumps(after, indent=2) + '\n')
audit['devAndTestFingerprintsUnchanged'] = True
queues = local_env.rabbit('list_queues', '-p', '/appoint-demo', 'name', 'messages_ready', 'messages_unacknowledged', 'consumers')
(out / 'rabbit-queues.txt').write_text(queues + '\n')
for line in queues.splitlines():
    parts = line.split()
    if len(parts) == 4 and parts[0].endswith('.dlq'):
        assert parts[1:3] == ['0', '0'], parts
cache = local_env.run(local_env.COMPOSE + ['exec', '-T', 'redis', 'redis-cli', '--scan', '--pattern', 'appoint:demo:catalog:v1:*'])
audit['redisCatalogKeyCount'] = len(cache.splitlines())
assert audit['redisCatalogKeyCount'] > 0
(out / 'audit.json').write_text(json.dumps(audit, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(audit, ensure_ascii=False, indent=2))
