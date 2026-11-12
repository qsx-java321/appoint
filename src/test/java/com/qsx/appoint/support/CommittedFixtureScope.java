package com.qsx.appoint.support;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 仅供类独占数据库的真实提交测试使用：按新增账号清理业务行，恢复本例修改的目录快照。
 * 不包裹被测业务事务，不重置自增序列，不用于共享数据库或并行方法。
 */
public final class CommittedFixtureScope implements AutoCloseable {
    private final JdbcTemplate jdbc;
    private final Set<Long> users;
    private final Set<String> events;
    private final Set<Long> inbox;
    private final Set<Long> notifications;
    private final Map<String, List<Map<String, Object>>> catalog = new LinkedHashMap<>();

    public CommittedFixtureScope(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        users = Set.copyOf(jdbc.queryForList("SELECT id FROM users", Long.class));
        events = Set.copyOf(jdbc.queryForList("SELECT event_id FROM payment_outbox_events", String.class));
        inbox = Set.copyOf(jdbc.queryForList("SELECT id FROM coordination_inbox_events", Long.class));
        notifications = Set.copyOf(jdbc.queryForList("SELECT id FROM payment_notifications", Long.class));
        for (String table : List.of("doctors", "schedules", "booking_settings")) {
            catalog.put(table, jdbc.queryForList("SELECT * FROM " + table));
        }
    }

    @Override
    public void close() {
        // 新事件有独立ID，先删Inbox/Outbox，避免未发布夹具影响下一例的全库扫描。
        for (Long id : jdbc.queryForList("SELECT id FROM coordination_inbox_events", Long.class)) {
            if (!inbox.contains(id)) jdbc.update("DELETE FROM coordination_inbox_events WHERE id=?", id);
        }
        for (String id : jdbc.queryForList("SELECT event_id FROM payment_outbox_events", String.class)) {
            if (!events.contains(id)) jdbc.update("DELETE FROM payment_outbox_events WHERE event_id=?", id);
        }
        for (Long id : jdbc.queryForList("SELECT id FROM payment_notifications", Long.class)) {
            if (!notifications.contains(id)) jdbc.update("DELETE FROM payment_notifications WHERE id=?", id);
        }
        for (Long id : jdbc.queryForList("SELECT id FROM users", Long.class)) {
            if (!users.contains(id)) deleteAccount(id);
        }
        // 外键顺序：先新场次、后新医生；原目录逐列还原，避免硬编码seed ID或容量。
        deleteNewCatalogRows("schedules");
        deleteNewCatalogRows("doctors");
        restore("doctors");
        restore("schedules");
        restore("booking_settings");
    }

    private void deleteAccount(long id) {
        String orders = "SELECT id FROM appointment_orders WHERE user_id=?";
        String payments = "SELECT id FROM payment_orders WHERE user_id=?";
        jdbc.update("DELETE FROM visit_records WHERE appointment_order_id IN (" + orders + ")", id);
        jdbc.update("DELETE FROM payment_refunds WHERE payment_order_id IN (" + payments + ")", id);
        jdbc.update("DELETE FROM payment_attempts WHERE payment_order_id IN (" + payments + ")", id);
        jdbc.update("DELETE FROM appointment_active_claims WHERE appointment_order_id IN (" + orders + ")", id);
        jdbc.update("DELETE FROM appointment_create_requests WHERE user_id=?", id);
        jdbc.update("DELETE FROM payment_orders WHERE user_id=?", id);
        jdbc.update("DELETE FROM appointment_orders WHERE user_id=?", id);
        jdbc.update("DELETE FROM patients WHERE user_id=?", id);
        jdbc.update("DELETE FROM schedule_inventory_adjustments WHERE operator_user_id=?", id);
        jdbc.update("UPDATE schedules SET closed_by=NULL WHERE closed_by=?", id);
        jdbc.update("UPDATE doctors SET user_id=NULL WHERE user_id=?", id);
        jdbc.update("DELETE FROM user_roles WHERE user_id=?", id);
        jdbc.update("DELETE FROM users WHERE id=?", id);
    }

    private void deleteNewCatalogRows(String table) {
        Set<Object> previous = catalog.get(table).stream().map(row -> row.get("id")).collect(Collectors.toSet());
        for (Map<String, Object> row : jdbc.queryForList("SELECT id FROM " + table)) {
            if (!previous.contains(row.get("id"))) jdbc.update("DELETE FROM " + table + " WHERE id=?", row.get("id"));
        }
    }

    private void restore(String table) {
        for (Map<String, Object> row : catalog.get(table)) {
            var columns = row.keySet().stream().filter(key -> !key.equals("id")).toList();
            var values = new java.util.ArrayList<>(columns.stream().map(row::get).toList());
            values.add(row.get("id"));
            String assignments = columns.stream().map(key -> key + "=?").collect(Collectors.joining(","));
            jdbc.update("UPDATE " + table + " SET " + assignments + " WHERE id=?", values.toArray());
        }
    }
}
