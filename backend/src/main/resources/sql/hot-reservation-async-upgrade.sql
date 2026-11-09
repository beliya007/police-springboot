-- =============================================================================
-- 警力合理配置规划平台 —— 历史版本升级脚本
--
-- 说明：当前版本的完整建表脚本是 police-force-init.sql，
--      已包含全部表结构与唯一索引，不需要再执行本文件。
--
-- 本脚本仅用于「从更早的版本升级到当前版本」的存量环境，
--      新环境请直接执行 police-force-init.sql。
-- =============================================================================

USE police_force;

-- 异步受理账本：消费端双重幂等的数据库层保障。
-- officer_id 为警员维度，与 uk_allocation_officer_slot_active 口径一致。
CREATE TABLE IF NOT EXISTS allocation_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    officer_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    allocation_id BIGINT NULL,
    reject_code VARCHAR(64) NULL,
    reject_reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    UNIQUE KEY uk_allocation_request_id (request_id),
    KEY idx_allocation_request_user_created (user_id, created_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 站内通知的事件幂等键：通知只发一次。
ALTER TABLE user_notification
    ADD COLUMN event_id VARCHAR(191) NULL AFTER user_id,
    ADD UNIQUE KEY uk_user_notification_event_id (event_id);
