-- =============================================================================
-- 警力合理配置规划平台 —— 历史版本升级脚本
--
-- 说明：当前版本的完整建表脚本是 police-force-init.sql，
--      已包含 allocation_assignment 的 checked_in_at / auto_cancel_deadline 字段，
--      新环境不需要再执行本文件。
--
-- 本脚本仅用于「从更早的版本升级到当前版本」的存量环境。
-- =============================================================================

USE police_force;

SET @schema_name = DATABASE();

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'allocation_assignment' AND COLUMN_NAME = 'checked_in_at') = 0,
    'ALTER TABLE allocation_assignment ADD COLUMN checked_in_at DATETIME NULL AFTER slot_end_datetime',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'allocation_assignment' AND COLUMN_NAME = 'auto_cancel_deadline') = 0,
    'ALTER TABLE allocation_assignment ADD COLUMN auto_cancel_deadline DATETIME NULL AFTER checked_in_at',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE allocation_assignment
SET auto_cancel_deadline = DATE_ADD(slot_start_datetime, INTERVAL 15 MINUTE)
WHERE status = 'BOOKED'
  AND slot_start_datetime IS NOT NULL
  AND auto_cancel_deadline IS NULL;
