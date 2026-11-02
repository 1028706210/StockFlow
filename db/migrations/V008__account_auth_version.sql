-- 认证版本用于撤销既有会话，不改写现有密码与启用状态。
ALTER TABLE app_user ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
