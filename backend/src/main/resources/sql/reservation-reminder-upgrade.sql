-- =============================================================================
-- 警力合理配置规划平台 —— 历史版本升级脚本
--
-- 说明：当前版本的完整建表脚本是 police-force-init.sql，
--      已包含 allocation_reminder_task / user_notification 的完整结构，
--      新环境不需要再执行本文件。
--
-- 本脚本仅用于「从更早的版本升级到当前版本」的存量环境。
-- =============================================================================

USE police_force;

-- 调配提醒任务：签到前提醒与未签到自动取消。
CREATE TABLE IF NOT EXISTS allocation_reminder_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    allocation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    officer_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    remind_type VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(512) NOT NULL,
    plan_send_time DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    last_error_message VARCHAR(512) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    sent_at DATETIME NULL,
    UNIQUE KEY uk_allocation_reminder_allocation_type (allocation_id, remind_type),
    KEY idx_allocation_reminder_status_plan_send (status, plan_send_time),
    KEY idx_allocation_reminder_user_status (user_id, status)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(512) NOT NULL,
    related_allocation_id BIGINT NULL,
    reminder_task_id BIGINT NULL,
    is_read TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    UNIQUE KEY uk_user_notification_reminder_task (reminder_task_id),
    KEY idx_user_notification_user_read_created (user_id, is_read, created_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
