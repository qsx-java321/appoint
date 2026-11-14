package com.qsx.appoint.localenv;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Base64;
import java.util.Map;
import java.security.SecureRandom;

/** 常驻入口统一在任何业务Bean初始化前校验并持有实例寿命锁。 */
public class LocalEnvironmentLifecycle implements BeanFactoryPostProcessor, DisposableBean {
    private final ConfigurableEnvironment environment;
    private Connection lock;
    private String name;

    public LocalEnvironmentLifecycle(ConfigurableEnvironment environment) { this.environment = environment; }
    public String name() { return name; }
    public boolean reset() { return "demo".equals(name) || ("test".equals(name) && flag("reset-only")); }
    public boolean flag(String suffix) { return environment.getProperty("appoint.local-environment." + suffix, Boolean.class, false); }

    @Override public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) throws BeansException {
        if (!flag("enabled")) return;
        try {
            name = environment.getRequiredProperty("appoint.local-environment.name");
            LocalEnvironmentPolicy.validate(environment, name);
            if ("dev".equals(name) && !"classpath:/regression/".equals(environment.getProperty("spring.config.location"))
                    && !java.nio.file.Files.exists(java.nio.file.Path.of(".local-env/dev-cutover-ready")))
                throw new IllegalStateException("dev旧环境切换预检尚未完成");
            int port = environment.getRequiredProperty("server.port", Integer.class);
            try (var probe = new ServerSocket(port, 0, InetAddress.getByName("127.0.0.1"))) { /* 清理前检查端口 */ }
            lock = DriverManager.getConnection(environment.getRequiredProperty("spring.datasource.url"),
                    environment.getRequiredProperty("spring.datasource.username"), environment.getRequiredProperty("spring.datasource.password"));
            try (var statement = lock.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                statement.setString(1, "appoint.local." + name);
                try (var result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) throw new IllegalStateException("环境已有运行实例或正在重置");
                }
            }
            if (reset()) {
                byte[] bytes = new byte[48]; new SecureRandom().nextBytes(bytes);
                String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                // test重置密钥必须持久保存，普通重启继续使用；demo密钥只在本次进程有效。
                if ("test".equals(name) && !"classpath:/regression/".equals(environment.getProperty("spring.config.location"))) LocalEnvironmentFiles.rotateTestJwt(secret);
                environment.getPropertySources().addFirst(new MapPropertySource("localResetJwt", Map.of("appoint.jwt.secret", secret)));
            }
            System.out.printf("常驻环境=%s，后端端口=%d，业务时钟=实时；MockPay渠道状态仅存于内存%n", name, port);
        } catch (Exception error) {
            destroy();
            // 驱动异常可能包含连接串和凭据，不传播原始message。
            throw new IllegalStateException("常驻环境预检失败：检查目标配置、端口、实例锁和本地凭据（未执行业务初始化）");
        }
    }

    public void seed(JdbcTemplate jdbc) {
        if (name == null || name.equals("dev")) return;
        String password = environment.getRequiredProperty("appoint.local-environment.seed-password");
        seedAccount(jdbc, name + "-admin@example.local", password, "ADMIN");
        if (name.equals("demo")) {
            seedAccount(jdbc, "demo-user@example.local", password, "USER");
            seedAccount(jdbc, "demo-doctor@example.local", password, "DOCTOR");
            jdbc.update("UPDATE doctors SET user_id=(SELECT id FROM users WHERE email=?) WHERE id=1", "demo-doctor@example.local");
        }
    }
    private void seedAccount(JdbcTemplate jdbc, String email, String password, String role) {
        var encoder = new BCryptPasswordEncoder();
        var existing = jdbc.queryForList("SELECT password_hash FROM users WHERE email=?", String.class, email);
        if (!existing.isEmpty()) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM local_environment_seed_accounts s JOIN users u ON u.id=s.user_id WHERE s.email=? AND u.email=? AND s.role_code=?", Integer.class, email, email, role) != 1)
                throw new IllegalStateException("初始化账号冲突；已有账号保持原样");
            return;
        }
        jdbc.update("INSERT INTO users(email,password_hash) VALUES (?,?)", email, encoder.encode(password));
        jdbc.update("INSERT INTO local_environment_seed_accounts(email,user_id,role_code) SELECT email,id,? FROM users WHERE email=?", role, email);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u CROSS JOIN roles r WHERE u.email=? AND r.code IN ('USER',?)", email, role);
    }
    @Override public void destroy() {
        if (lock != null) {
            try { lock.close(); } catch (Exception ignored) { /* 连接关闭同时释放MySQL命名锁 */ }
            lock = null;
        }
    }
}
