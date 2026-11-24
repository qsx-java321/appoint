-- 用户头像：数据库只保存对象存储中的当前对象键和展示元数据，不保存图片内容。
-- 替换头像时生成新对象键，旧对象由应用在数据库更新成功后尽力删除。

ALTER TABLE users ADD COLUMN avatar_key VARCHAR(255);

ALTER TABLE users ADD COLUMN avatar_content_type VARCHAR(64);

ALTER TABLE users ADD COLUMN avatar_updated_at DATETIME;
