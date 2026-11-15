package com.qsx.appoint.localenv;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Locale;

/** 在启动事务内补充环境账号，保留已有密码、角色和人工医生绑定。 */
final class LocalEnvironmentAccounts {
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    LocalEnvironmentAccounts(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void seed(String environment, String password) {
        account(environment + "-admin@example.local", password, "ADMIN");
        for (int ordinal = 1; ordinal <= 18; ordinal++) {
            var allocations = jdbc.queryForList("SELECT id FROM doctor_number_allocations WHERE allocation_token=?",
                    Long.class, "SEED-DOCTOR-" + ordinal);
            if (allocations.isEmpty()) throw new IllegalStateException("预置医生编号分配缺失，请核对迁移记录");
            String doctorNo = String.format(Locale.ROOT, "DOC%06d", allocations.getFirst());
            var doctors = jdbc.queryForList("SELECT id FROM doctors WHERE doctor_no=?", Long.class, doctorNo);
            // 管理员已删除的样例档案不在普通启动时重新创建。
            if (doctors.isEmpty()) continue;
            long doctorId = doctors.getFirst();
            String email = String.format(Locale.ROOT, "%s-doctor-%03d@example.local", environment, ordinal);
            long userId = account(email, password, "DOCTOR");
            int updated = jdbc.update("UPDATE doctors SET user_id=? WHERE id=? AND (user_id IS NULL OR user_id=?)",
                    userId, doctorId, userId);
            if (updated != 1) throw new IllegalStateException("预置医生已有其他账号绑定，保留原绑定并停止初始化");
        }
        if (environment.equals("demo")) account("demo-user@example.local", password, "USER");
    }

    private long account(String email, String password, String role) {
        var existing = jdbc.queryForList("SELECT id FROM users WHERE email=?", Long.class, email);
        if (!existing.isEmpty()) {
            long id = existing.getFirst();
            if (jdbc.queryForObject("SELECT COUNT(*) FROM local_environment_seed_accounts WHERE email=? AND user_id=? AND role_code=?",
                    Integer.class, email, id, role) != 1)
                throw new IllegalStateException("初始化账号冲突；已有账号保持原样");
            return id;
        }
        jdbc.update("INSERT INTO users(email,password_hash) VALUES (?,?)", email, encoder.encode(password));
        long id = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        jdbc.update("INSERT INTO local_environment_seed_accounts(email,user_id,role_code) VALUES (?,?,?)", email, id, role);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code IN ('USER',?)", id, role);
        return id;
    }
}
