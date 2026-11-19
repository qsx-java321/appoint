package com.qsx.appoint.localenv;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
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
            acquireRunLock();
            int port = environment.getRequiredProperty("server.port", Integer.class);
            checkPort(port);
            checkPort(LocalEnvironmentPolicy.FRONTEND_PORT);
            if (reset()) {
                byte[] bytes = new byte[48]; new SecureRandom().nextBytes(bytes);
                String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                // test重置密钥必须持久保存，普通重启继续使用；demo密钥只在本次进程有效。
                if ("test".equals(name) && !"classpath:/regression/".equals(environment.getProperty("spring.config.location"))) LocalEnvironmentFiles.rotateTestJwt(secret);
                environment.getPropertySources().addFirst(new MapPropertySource("localResetJwt", Map.of("appoint.jwt.secret", secret)));
            }
            System.out.printf("常驻环境=%s，后端端口=%d，业务时钟=实时%n", name, port);
        } catch (RuntimeException error) {
            destroy();
            throw error;
        }
    }

    private void acquireRunLock() {
        try {
            lock = DriverManager.getConnection(environment.getRequiredProperty("spring.datasource.url"),
                    environment.getRequiredProperty("spring.datasource.username"),
                    environment.getRequiredProperty("spring.datasource.password"));
            try (var statement = lock.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                statement.setString(1, "appoint.local.runtime");
                try (var result = statement.executeQuery()) {
                    if (result.next() && result.getInt(1) == 1) return;
                }
            }
        } catch (SQLException error) {
            // 驱动异常可能包含连接串和凭据。
            throw new IllegalStateException("无法取得常驻环境运行锁，请检查MySQL及本地配置");
        }
        throw new IllegalStateException("已有常驻环境正在启动、运行或重置，请先停止当前环境");
    }

    private void checkPort(int port) {
        try (var probe = new ServerSocket()) {
            probe.setReuseAddress(true);
            probe.bind(new InetSocketAddress("127.0.0.1", port));
        } catch (IOException error) {
            throw new IllegalStateException("端口" + port + "被占用，请从原入口停止当前前后端");
        }
    }

    public void seed(JdbcTemplate jdbc) {
        if (name == null) return;
        String password = environment.getRequiredProperty("appoint.local-environment.seed-password");
        new LocalEnvironmentAccounts(jdbc).seed(name, password);
    }
    @Override public void destroy() {
        if (lock != null) {
            try { lock.close(); } catch (Exception ignored) { /* 连接关闭同时释放MySQL命名锁 */ }
            lock = null;
        }
    }
}
