"""本轮 test 数据只读审计；不包含账号凭据与完整证件。"""
import json
import sys
import time
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
import local_env as env
OUT=Path(__file__).parent/'evidence'
records=json.loads((OUT/'records.json').read_text())
prefix='workflow-'+records['run']+'-%'
scope=f"a.user_id IN (SELECT id FROM users WHERE email LIKE '{prefix}')"
def sql(statement): return env.mysql('USE appoint_test; '+statement)
until=time.monotonic()+30
while sql(f"SELECT COUNT(*) FROM payment_outbox_events o JOIN payment_orders p ON p.id=o.aggregate_id JOIN appointment_orders a ON a.id=p.appointment_order_id WHERE {scope} AND o.status<>'PUBLISHED';")!='0':
    assert time.monotonic()<until,'Outbox 尚未全部发布'
    time.sleep(.5)
audit={
 'orders':sql(f"SELECT a.id,a.doctor_id,a.appointment_date,a.start_time,a.status,p.status,COALESCE(v.status,IF(a.status='CONFIRMED','NOT_CHECKED_IN','NONE')),COALESCE(a.visit_number,0) FROM appointment_orders a JOIN payment_orders p ON p.appointment_order_id=a.id LEFT JOIN visit_records v ON v.appointment_order_id=a.id WHERE {scope} ORDER BY a.id;"),
 'inventory':sql(f"SELECT s.id,s.total_capacity,s.available_capacity,s.held_capacity,s.confirmed_capacity,s.next_visit_number FROM schedules s WHERE s.id IN (SELECT a.schedule_id FROM appointment_orders a WHERE {scope}) ORDER BY s.id;"),
 'outbox':sql(f"SELECT o.event_type,o.status,COUNT(*) FROM payment_outbox_events o JOIN payment_orders p ON p.id=o.aggregate_id JOIN appointment_orders a ON a.id=p.appointment_order_id WHERE {scope} GROUP BY o.event_type,o.status;"),
 'inbox':sql(f"SELECT i.event_type,COUNT(*) FROM coordination_inbox_events i JOIN payment_outbox_events o ON o.event_id=i.event_id JOIN payment_orders p ON p.id=o.aggregate_id JOIN appointment_orders a ON a.id=p.appointment_order_id WHERE {scope} GROUP BY i.event_type;"),
 'refunds':sql(f"SELECT r.status,COUNT(*) FROM payment_refunds r JOIN payment_orders p ON p.id=r.payment_order_id JOIN appointment_orders a ON a.id=p.appointment_order_id WHERE {scope} GROUP BY r.status;"),
 'invariants':{
  'brokenInventory':sql('SELECT COUNT(*) FROM schedules WHERE total_capacity<>available_capacity+held_capacity+confirmed_capacity;'),
  'closedActiveClaims':sql(f"SELECT COUNT(*) FROM appointment_active_claims c JOIN appointment_orders a ON a.id=c.appointment_order_id WHERE {scope} AND a.status NOT IN ('PENDING_PAYMENT','CONFIRMED');"),
  'duplicateVisitNumbers':sql(f"SELECT COUNT(*) FROM (SELECT a.schedule_id,a.visit_number FROM appointment_orders a WHERE {scope} AND a.visit_number IS NOT NULL GROUP BY a.schedule_id,a.visit_number HAVING COUNT(*)>1) dup;"),
  'missingPaymentInbox':sql(f"SELECT COUNT(*) FROM payment_outbox_events o JOIN payment_orders p ON p.id=o.aggregate_id JOIN appointment_orders a ON a.id=p.appointment_order_id LEFT JOIN coordination_inbox_events i ON i.event_id=o.event_id WHERE {scope} AND o.event_type='PaymentSucceeded' AND i.event_id IS NULL;"),
  'unfinishedRefunds':sql(f"SELECT COUNT(*) FROM payment_refunds r JOIN payment_orders p ON p.id=r.payment_order_id JOIN appointment_orders a ON a.id=p.appointment_order_id WHERE {scope} AND r.status<>'SUCCEEDED';")
 }
}
for name,value in audit['invariants'].items(): assert value=='0',(name,value)
queues=env.rabbit('list_queues','-p','/appoint-test','name','messages_ready','messages_unacknowledged','consumers')
(OUT/'rabbit-queues.txt').write_text(queues+'\n')
for line in queues.splitlines():
    parts=line.split()
    if len(parts)==4 and parts[0].endswith('.dlq'): assert parts[1:3]==['0','0'],parts
cache=env.run(env.COMPOSE+['exec','-T','redis','redis-cli','--scan','--pattern','appoint:test:catalog:v1:*'])
audit['redisCatalogKeyCount']=len(cache.splitlines())
assert audit['redisCatalogKeyCount']>0
audit['processes']=env.status('test')
(OUT/'database-audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(audit,ensure_ascii=False,indent=2))
