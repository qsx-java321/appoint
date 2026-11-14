-- 环境初始化来源标记；不包含密码，dev不自动新增任何账号。
CREATE TABLE local_environment_seed_accounts (
    email VARCHAR(120) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    CONSTRAINT fk_local_seed_user FOREIGN KEY (user_id) REFERENCES users (id)
);
