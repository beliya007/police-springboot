-- =============================================================================
-- 警力合理配置规划平台 —— 全量建表脚本
--
-- 覆盖范围：
--   1. 通用域        : sys_user / sys_dict_* / message_outbox / admin_audit_log / user_notification
--   2. 警力配置域    : police_station / duty_module / station_module_config / police_officer
--                      / efficiency_factor
--   3. 警力调配域    : duty_slot / allocation_assignment / allocation_request / allocation_reminder_task
--   4. 体检预约域    : exam_batch / exam_slot / exam_appointment / exam_appointment_request
--                      / exam_result / exam_waiting
--   5. 效能计算域    : calc_version_allocator / recalculation_request / recalculation_shard
--                      / efficiency_result / station_efficiency
--
-- 并发设计要点（与普通 CRUD 脚本的差别）：
--   * 所有"名额"表都用条件更新 UPDATE ... WHERE remain_quota >= :need 扣减，
--     受影响行数为 0 即判定售罄，杜绝超卖。
--   * allocation_assignment 用 UNIQUE KEY (officer_id, slot_id, is_active) 保证
--     「同一名警员不能被两个单位同时派出」；is_active 取消后置 NULL，
--     MySQL 唯一索引中 NULL 不参与去重，因此允许重新申请。
--   * exam_appointment 同样用 UNIQUE KEY (officer_id, slot_id, is_active) 保证「一人一档」。
--   * exam_waiting 用 UNIQUE KEY (slot_id, officer_id) 保证候补幂等。
--   * request_id 类唯一键是消费端双重幂等的数据库层保障。
--   * station_efficiency.latest_calc_version 是乐观 CAS 锚点。
--   * calc_version_allocator 用自增主键模拟严格单调递增的版本序列。
-- =============================================================================

CREATE DATABASE IF NOT EXISTS police_force
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE police_force;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS admin_audit_log;
DROP TABLE IF EXISTS message_outbox;
DROP TABLE IF EXISTS user_notification;
DROP TABLE IF EXISTS allocation_reminder_task;
DROP TABLE IF EXISTS allocation_request;
DROP TABLE IF EXISTS allocation_assignment;
DROP TABLE IF EXISTS duty_slot;
DROP TABLE IF EXISTS exam_waiting;
DROP TABLE IF EXISTS exam_result;
DROP TABLE IF EXISTS exam_appointment_request;
DROP TABLE IF EXISTS exam_appointment;
DROP TABLE IF EXISTS exam_slot;
DROP TABLE IF EXISTS exam_batch;
DROP TABLE IF EXISTS station_efficiency;
DROP TABLE IF EXISTS efficiency_result;
DROP TABLE IF EXISTS recalculation_shard;
DROP TABLE IF EXISTS recalculation_request;
DROP TABLE IF EXISTS calc_version_allocator;
DROP TABLE IF EXISTS efficiency_factor;
DROP TABLE IF EXISTS police_officer;
DROP TABLE IF EXISTS station_module_config;
DROP TABLE IF EXISTS duty_module;
DROP TABLE IF EXISTS police_station;
DROP TABLE IF EXISTS sys_dict_data;
DROP TABLE IF EXISTS sys_dict_type;
DROP TABLE IF EXISTS sys_user;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 1. 通用域
-- =============================================================================

CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    phone VARCHAR(32) NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_role_status (role, status)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE sys_dict_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dict_name VARCHAR(100) NOT NULL,
    dict_type VARCHAR(100) NOT NULL,
    UNIQUE KEY uk_sys_dict_type (dict_type)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE sys_dict_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dict_type VARCHAR(100) NOT NULL,
    dict_label VARCHAR(100) NOT NULL,
    dict_value VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_default CHAR(1) NOT NULL DEFAULT 'N',
    UNIQUE KEY uk_sys_dict_data_type_value (dict_type, dict_value),
    KEY idx_sys_dict_data_type_sort (dict_type, sort_order)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Transactional outbox：业务结果与事件在同一本地事务提交，dispatcher 负责重试投递。
CREATE TABLE message_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(191) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    tag VARCHAR(64) NOT NULL,
    message_key VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retry_count INT NOT NULL DEFAULT 0,
    locked_until DATETIME NULL,
    last_error_message VARCHAR(512) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    sent_at DATETIME NULL,
    UNIQUE KEY uk_message_outbox_event_id (event_id),
    KEY idx_message_outbox_status_available_id (status, available_at, id),
    KEY idx_message_outbox_status_locked_until (status, locked_until),
    KEY idx_message_outbox_aggregate (aggregate_type, aggregate_id),
    KEY idx_message_outbox_event_type_status (event_type, status)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE admin_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_username VARCHAR(64) NOT NULL,
    module VARCHAR(32) NOT NULL,
    action VARCHAR(32) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NULL,
    result VARCHAR(16) NOT NULL,
    summary TEXT NULL,
    error_message VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_admin_audit_event_id (event_id),
    KEY idx_admin_audit_operator_id (operator_id),
    KEY idx_admin_audit_module_action (module, action),
    KEY idx_admin_audit_created_at (created_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE user_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    event_id VARCHAR(191) NULL,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(512) NOT NULL,
    related_allocation_id BIGINT NULL,
    related_exam_appointment_id BIGINT NULL,
    reminder_task_id BIGINT NULL,
    is_read TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    UNIQUE KEY uk_user_notification_event_id (event_id),
    UNIQUE KEY uk_user_notification_reminder_task (reminder_task_id),
    KEY idx_user_notification_user_read_created (user_id, is_read, created_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
-- 2. 警力配置域
-- =============================================================================

CREATE TABLE police_station (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    station_code VARCHAR(50) NOT NULL,
    station_name VARCHAR(100) NOT NULL,
    district VARCHAR(100) NULL,
    station_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    location VARCHAR(255) NULL,
    description TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_police_station_code (station_code),
    KEY idx_police_station_type_status (station_type, status),
    KEY idx_police_station_district (district)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE duty_module (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_code VARCHAR(50) NOT NULL,
    module_name VARCHAR(100) NOT NULL,
    ratio INT NOT NULL DEFAULT 0,
    physical_required TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_duty_module_code (module_code),
    KEY idx_duty_module_status (status)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 某警局某模块的核定配置人数（唯一键保证一局一模块一条配置）
CREATE TABLE station_module_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    station_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    configured_headcount INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_station_module_config (station_id, module_id),
    KEY idx_station_module_config_station (station_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE police_officer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    officer_code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    -- 关联的登录账号。体检预约的「本人操作」通过它把 JWT 主体映射到警员。
    user_id BIGINT NULL,
    station_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    gender VARCHAR(16) NOT NULL,
    birth_date DATETIME NOT NULL,
    rank_code VARCHAR(32) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_police_officer_code (officer_code),
    UNIQUE KEY uk_police_officer_user_id (user_id),
    KEY idx_police_officer_station_module (station_id, module_id),
    KEY idx_police_officer_status (status),
    KEY idx_police_officer_gender (gender),
    KEY idx_police_officer_birth_date (birth_date)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 效能因素权重：同一 factor_type 内所有 factor_value 的 weight 之和必须等于 100
CREATE TABLE efficiency_factor (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    factor_type VARCHAR(32) NOT NULL,
    factor_value VARCHAR(64) NOT NULL,
    weight INT NOT NULL DEFAULT 0,
    injury_penalty INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_efficiency_factor_type_value (factor_type, factor_value),
    KEY idx_efficiency_factor_type_status (factor_type, status)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
-- 3. 警力调配域
-- =============================================================================

CREATE TABLE duty_slot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    station_id BIGINT NOT NULL,
    module_id BIGINT NULL,
    start_datetime DATETIME NOT NULL,
    end_datetime DATETIME NOT NULL,
    slot_type VARCHAR(16) NOT NULL,
    open_time DATETIME NULL,
    total_quota INT NOT NULL,
    remain_quota INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_duty_slot_station (station_id),
    KEY idx_duty_slot_status (status),
    KEY idx_duty_slot_type (slot_type),
    KEY idx_duty_slot_start (start_datetime)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 警力调配单（上岗单）。
-- 关键：uk_allocation_officer_slot_active 保证同一名警员在同一名额时段不会被两个单位同时派出。
CREATE TABLE allocation_assignment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    allocation_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    officer_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    station_name VARCHAR(100) NOT NULL,
    station_code VARCHAR(50) NOT NULL,
    station_location VARCHAR(255) NULL,
    slot_start_datetime DATETIME NOT NULL,
    slot_end_datetime DATETIME NOT NULL,
    checked_in_at DATETIME NULL,
    auto_cancel_deadline DATETIME NULL,
    is_active TINYINT NULL,
    status VARCHAR(16) NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    cancel_reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_allocation_no (allocation_no),
    UNIQUE KEY uk_allocation_officer_slot_active (officer_id, slot_id, is_active),
    KEY idx_allocation_user_id (user_id),
    KEY idx_allocation_officer_id (officer_id),
    KEY idx_allocation_station_id (station_id),
    KEY idx_allocation_slot_id (slot_id),
    KEY idx_allocation_status (status),
    KEY idx_allocation_created_at (created_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 异步受理账本：消费端双重幂等的数据库层保障
CREATE TABLE allocation_request (
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
    KEY idx_allocation_request_user_created (user_id, created_at),
    KEY idx_allocation_request_status (status)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE allocation_reminder_task (
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

-- =============================================================================
-- 4. 体检预约域
-- =============================================================================

CREATE TABLE exam_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_no VARCHAR(64) NOT NULL,
    batch_name VARCHAR(128) NOT NULL,
    institution VARCHAR(128) NULL,
    release_time DATETIME NOT NULL,
    close_time DATETIME NOT NULL,
    total_quota INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PREPARED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_exam_batch_no (batch_no),
    KEY idx_exam_batch_status_release (status, release_time)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE exam_slot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    slot_type VARCHAR(16) NOT NULL,
    start_datetime DATETIME NOT NULL,
    end_datetime DATETIME NOT NULL,
    open_time DATETIME NULL,
    total_quota INT NOT NULL,
    remain_quota INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_exam_slot_batch (batch_id),
    KEY idx_exam_slot_status (status),
    KEY idx_exam_slot_type (slot_type),
    KEY idx_exam_slot_start (start_datetime)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 体检预约单：一人一档由 uk_exam_appointment_officer_slot_active 保证
CREATE TABLE exam_appointment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    appointment_no VARCHAR(64) NOT NULL,
    officer_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    officer_name VARCHAR(64) NOT NULL,
    slot_start_datetime DATETIME NOT NULL,
    slot_end_datetime DATETIME NOT NULL,
    is_active TINYINT NULL,
    status VARCHAR(16) NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    from_waiting TINYINT NOT NULL DEFAULT 0,
    cancel_reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_exam_appointment_no (appointment_no),
    UNIQUE KEY uk_exam_appointment_officer_slot_active (officer_id, slot_id, is_active),
    KEY idx_exam_appointment_slot_id (slot_id),
    KEY idx_exam_appointment_batch_id (batch_id),
    KEY idx_exam_appointment_officer_id (officer_id),
    KEY idx_exam_appointment_status (status),
    KEY idx_exam_appointment_created_at (created_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE exam_appointment_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id VARCHAR(36) NOT NULL,
    officer_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    appointment_id BIGINT NULL,
    waiting_id BIGINT NULL,
    reject_code VARCHAR(64) NULL,
    reject_reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    UNIQUE KEY uk_exam_appointment_request_id (request_id),
    KEY idx_exam_appointment_request_officer_created (officer_id, created_at),
    KEY idx_exam_appointment_request_status (status)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 体检结论：驱动有效警力口径与增量重算
CREATE TABLE exam_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    appointment_id BIGINT NOT NULL,
    officer_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    conclusion VARCHAR(16) NOT NULL,
    conclusion_note VARCHAR(512) NULL,
    examined_at DATETIME NOT NULL,
    recorded_by VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_exam_result_appointment (appointment_id),
    KEY idx_exam_result_officer (officer_id),
    KEY idx_exam_result_station_module (station_id, module_id),
    KEY idx_exam_result_conclusion (conclusion)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 候补记录：Redis ZSet 为主 + 本表持久化快照，(slot_id, officer_id) 唯一键保证递补幂等
CREATE TABLE exam_waiting (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    slot_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    officer_id BIGINT NOT NULL,
    sequence_no BIGINT NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'WAITING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_exam_waiting_slot_officer (slot_id, officer_id),
    UNIQUE KEY uk_exam_waiting_request (request_id),
    KEY idx_exam_waiting_slot_status_seq (slot_id, status, sequence_no),
    KEY idx_exam_waiting_status (status)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
-- 5. 效能计算域
-- =============================================================================

-- 计算版本号发号器：用自增主键模拟严格单调递增的序列。
-- 版本号是结果覆盖的乐观 CAS 锚点，必须全局单调且不回退。
CREATE TABLE calc_version_allocator (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    allocated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_calc_version_allocator_allocated_at (allocated_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE recalculation_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id VARCHAR(36) NOT NULL,
    batch_no VARCHAR(64) NOT NULL,
    trigger_source VARCHAR(32) NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_id BIGINT NULL,
    calc_version BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    affected_station_count INT NOT NULL DEFAULT 0,
    reject_code VARCHAR(64) NULL,
    reject_reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    UNIQUE KEY uk_recalculation_request_id (request_id),
    KEY idx_recalculation_request_batch (batch_no),
    KEY idx_recalculation_request_status (status),
    KEY idx_recalculation_request_created (created_at)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 重算分片：把「每警局一条消息」压成「每分片一条消息」，消除 N 次全表扫描的读放大
CREATE TABLE recalculation_shard (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_no VARCHAR(64) NOT NULL,
    shard_index INT NOT NULL,
    station_ids TEXT NOT NULL,
    station_count INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    last_error_message VARCHAR(512) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_recalculation_shard_batch_index (batch_no, shard_index),
    KEY idx_recalculation_shard_status (status)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE efficiency_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    station_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    calc_version BIGINT NOT NULL,
    batch_no VARCHAR(64) NOT NULL,
    configured_headcount INT NOT NULL,
    effective_headcount INT NOT NULL,
    average_officer_score DOUBLE NOT NULL,
    module_score DOUBLE NOT NULL,
    module_ratio INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_efficiency_result_station_module_version (station_id, module_id, calc_version),
    KEY idx_efficiency_result_station (station_id),
    KEY idx_efficiency_result_batch (batch_no)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 警局效能最新态：latest_calc_version 是乐观 CAS 锚点。
-- 只有更大的 calc_version 才允许覆盖，杜绝「旧权重 + 新人数」的错误组合。
CREATE TABLE station_efficiency (
    station_id BIGINT PRIMARY KEY,
    latest_calc_version BIGINT NOT NULL DEFAULT 0,
    station_score DOUBLE NOT NULL DEFAULT 0,
    configured_headcount INT NOT NULL DEFAULT 0,
    effective_headcount INT NOT NULL DEFAULT 0,
    actual_effectiveness DOUBLE NOT NULL DEFAULT 0,
    shortage_headcount INT NOT NULL DEFAULT 0,
    calculated_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- =============================================================================
-- 种子数据
-- =============================================================================

INSERT INTO sys_user (id, username, password_hash, nickname, role, phone, status, created_at, updated_at)
VALUES
    (1, 'admin', '123456', '系统管理员', 'ADMIN', '13800000000', 'ACTIVE', '2026-04-20 09:00:00', '2026-04-20 09:00:00'),
    (2, 'tester', '123456', '测试用户', 'USER', '13900000000', 'ACTIVE', '2026-04-20 09:05:00', '2026-04-20 09:05:00'),
    (3, 'locked_user', '123456', '锁定用户', 'USER', '13700000000', 'LOCKED', '2026-04-20 09:10:00', '2026-04-20 09:10:00');

INSERT INTO sys_dict_type (id, dict_name, dict_type)
VALUES
    (1, '警局类型', 'station_type'),
    (2, '警局状态', 'station_status'),
    (3, '效能因素维度', 'efficiency_factor_type'),
    (4, '体检结论', 'exam_conclusion');

INSERT INTO sys_dict_data (id, dict_type, dict_label, dict_value, sort_order, is_default)
VALUES
    (1, 'station_type', '分局', 'DIVISION', 1, 'Y'),
    (2, 'station_type', '派出所', 'SUB_STATION', 2, 'N'),
    (3, 'station_type', '警区', 'PRECINCT', 3, 'N'),
    (4, 'station_status', '可用', 'AVAILABLE', 1, 'Y'),
    (5, 'station_status', '维护中', 'MAINTAINING', 2, 'N'),
    (6, 'station_status', '已停用', 'DISABLED', 3, 'N'),
    (7, 'efficiency_factor_type', '年龄', 'AGE', 1, 'Y'),
    (8, 'efficiency_factor_type', '性别', 'GENDER', 2, 'N'),
    (9, 'exam_conclusion', '合格', 'PASS', 1, 'Y'),
    (10, 'exam_conclusion', '不合格', 'FAIL', 2, 'N'),
    (11, 'exam_conclusion', '有伤病', 'INJURED', 3, 'N');

INSERT INTO police_station (id, station_code, station_name, district, station_type, status, location, description, created_at, updated_at)
VALUES
    (1, 'PS-01', '东城分局', '东城区', 'DIVISION', 'AVAILABLE', '东城区中心路 1 号', '辖区最大分局，警力基数最大。', '2026-04-20 09:20:00', '2026-04-20 09:20:00'),
    (2, 'PS-02', '滨江分局', '滨江区', 'DIVISION', 'AVAILABLE', '滨江区滨江大道 8 号', '承担大型活动安保任务。', '2026-04-20 09:21:00', '2026-04-20 09:21:00'),
    (3, 'PS-03', '西关派出所', '西关区', 'SUB_STATION', 'AVAILABLE', '西关区西关路 22 号', '基层所队，警力紧张。', '2026-04-20 09:22:00', '2026-04-20 09:22:00'),
    (4, 'PS-04', '南山警区', '南山区', 'PRECINCT', 'MAINTAINING', '南山区南山大道 5 号', '正在调整警力结构。', '2026-04-20 09:23:00', '2026-04-20 09:23:00');

INSERT INTO duty_module (id, module_code, module_name, ratio, physical_required, status, created_at, updated_at)
VALUES
    (1, 'PATROL', '巡逻', 30, 1, 'ENABLED', '2026-04-20 09:25:00', '2026-04-20 09:25:00'),
    (2, 'HOUSEHOLD', '户籍', 20, 0, 'ENABLED', '2026-04-20 09:25:00', '2026-04-20 09:25:00'),
    (3, 'TRAFFIC', '交警', 25, 1, 'ENABLED', '2026-04-20 09:25:00', '2026-04-20 09:25:00'),
    (4, 'CRIMINAL', '刑侦', 15, 1, 'ENABLED', '2026-04-20 09:25:00', '2026-04-20 09:25:00'),
    (5, 'COMMAND', '指挥', 10, 0, 'ENABLED', '2026-04-20 09:25:00', '2026-04-20 09:25:00');

INSERT INTO station_module_config (id, station_id, module_id, configured_headcount, created_at, updated_at)
VALUES
    (1, 1, 1, 120, '2026-04-20 09:26:00', '2026-04-20 09:26:00'),
    (2, 1, 2, 80, '2026-04-20 09:26:00', '2026-04-20 09:26:00'),
    (3, 1, 3, 100, '2026-04-20 09:26:00', '2026-04-20 09:26:00'),
    (4, 1, 4, 60, '2026-04-20 09:26:00', '2026-04-20 09:26:00'),
    (5, 1, 5, 40, '2026-04-20 09:26:00', '2026-04-20 09:26:00'),
    (6, 2, 1, 100, '2026-04-20 09:27:00', '2026-04-20 09:27:00'),
    (7, 2, 2, 60, '2026-04-20 09:27:00', '2026-04-20 09:27:00'),
    (8, 2, 3, 90, '2026-04-20 09:27:00', '2026-04-20 09:27:00'),
    (9, 2, 4, 50, '2026-04-20 09:27:00', '2026-04-20 09:27:00'),
    (10, 2, 5, 30, '2026-04-20 09:27:00', '2026-04-20 09:27:00'),
    (11, 3, 1, 45, '2026-04-20 09:28:00', '2026-04-20 09:28:00'),
    (12, 3, 2, 30, '2026-04-20 09:28:00', '2026-04-20 09:28:00'),
    (13, 3, 3, 35, '2026-04-20 09:28:00', '2026-04-20 09:28:00'),
    (14, 4, 1, 25, '2026-04-20 09:29:00', '2026-04-20 09:29:00');

-- 效能因素：AGE 与 GENDER 两个维度的权重和各自等于 100
INSERT INTO efficiency_factor (id, factor_type, factor_value, weight, injury_penalty, status, created_at, updated_at)
VALUES
    (1, 'AGE', '25以下', 20, 10, 'ENABLED', '2026-04-20 09:30:00', '2026-04-20 09:30:00'),
    (2, 'AGE', '25-35', 35, 5, 'ENABLED', '2026-04-20 09:30:00', '2026-04-20 09:30:00'),
    (3, 'AGE', '35-45', 30, 5, 'ENABLED', '2026-04-20 09:30:00', '2026-04-20 09:30:00'),
    (4, 'AGE', '45-55', 15, 10, 'ENABLED', '2026-04-20 09:30:00', '2026-04-20 09:30:00'),
    (5, 'AGE', '55以上', 0, 20, 'ENABLED', '2026-04-20 09:30:00', '2026-04-20 09:30:00'),
    (6, 'GENDER', 'MALE', 50, 0, 'ENABLED', '2026-04-20 09:31:00', '2026-04-20 09:31:00'),
    (7, 'GENDER', 'FEMALE', 50, 0, 'ENABLED', '2026-04-20 09:31:00', '2026-04-20 09:31:00');

-- 警员种子数据：覆盖多个年龄段与性别，用于验证效能计算
INSERT INTO police_officer (id, officer_code, name, user_id, station_id, module_id, gender, birth_date, rank_code, status, created_at, updated_at)
VALUES
    (1, 'OF001', '张伟', 2, 1, 1, 'MALE', '1995-06-15 00:00:00', 'PC', 'ACTIVE', '2026-04-20 09:40:00', '2026-04-20 09:40:00'),
    (2, 'OF002', '李娜', NULL, 1, 1, 'FEMALE', '1998-03-20 00:00:00', 'PC', 'ACTIVE', '2026-04-20 09:40:00', '2026-04-20 09:40:00'),
    (3, 'OF003', '王强', NULL, 1, 2, 'MALE', '1985-11-02 00:00:00', 'SGT', 'ACTIVE', '2026-04-20 09:40:00', '2026-04-20 09:40:00'),
    (4, 'OF004', '刘洋', NULL, 2, 1, 'MALE', '1992-07-11 00:00:00', 'PC', 'ACTIVE', '2026-04-20 09:40:00', '2026-04-20 09:40:00'),
    (5, 'OF005', '陈静', NULL, 2, 3, 'FEMALE', '1990-01-25 00:00:00', 'SGT', 'ACTIVE', '2026-04-20 09:40:00', '2026-04-20 09:40:00');

-- 警力调配名额：一个 HOT 名额用于演示「名额 + 警员唯一占用」双重互斥
INSERT INTO duty_slot (id, station_id, module_id, start_datetime, end_datetime, slot_type, open_time, total_quota, remain_quota, status, created_at, updated_at)
VALUES
    (1, 1, 1, '2026-04-21 09:00:00', '2026-04-21 11:00:00', 'NORMAL', NULL, 4, 4, 'OPEN', '2026-04-20 09:45:00', '2026-04-20 09:45:00'),
    (2, 2, 1, '2026-04-22 14:00:00', '2026-04-22 16:00:00', 'HOT', '2026-04-20 09:00:00', 8, 8, 'OPEN', '2026-04-20 09:46:00', '2026-04-20 09:46:00'),
    (3, 3, 2, '2026-04-21 13:00:00', '2026-04-21 15:00:00', 'NORMAL', NULL, 3, 3, 'OPEN', '2026-04-20 09:47:00', '2026-04-20 09:47:00'),
    (4, 1, 3, '2026-04-23 10:00:00', '2026-04-23 12:00:00', 'NORMAL', NULL, 1, 1, 'OPEN', '2026-04-20 09:48:00', '2026-04-20 09:48:00'),
    (5, 2, 1, '2026-04-24 19:00:00', '2026-04-24 21:00:00', 'HOT', '2026-04-23 12:00:00', 8, 8, 'OPEN', '2026-04-20 09:49:00', '2026-04-20 09:49:00'),
    (6, 4, 1, '2026-04-25 09:00:00', '2026-04-25 12:00:00', 'NORMAL', NULL, 2, 2, 'CLOSED', '2026-04-20 09:50:00', '2026-04-20 09:50:00');

-- 体检批次与时段：批次名额 70，用于 500 并发抢名额压测
INSERT INTO exam_batch (id, batch_no, batch_name, institution, release_time, close_time, total_quota, status, created_at, updated_at)
VALUES
    (1, 'EXAM2026Q2', '2026 年第二季度集中体检', '市体检中心', '2026-04-21 08:00:00', '2026-04-30 18:00:00', 70, 'RELEASED', '2026-04-20 10:00:00', '2026-04-20 10:00:00');

INSERT INTO exam_slot (id, batch_id, slot_type, start_datetime, end_datetime, open_time, total_quota, remain_quota, status, created_at, updated_at)
VALUES
    (1, 1, 'HOT', '2026-04-22 08:00:00', '2026-04-22 12:00:00', '2026-04-21 08:00:00', 40, 40, 'OPEN', '2026-04-20 10:01:00', '2026-04-20 10:01:00'),
    (2, 1, 'HOT', '2026-04-22 13:00:00', '2026-04-22 17:00:00', '2026-04-21 08:00:00', 30, 30, 'OPEN', '2026-04-20 10:02:00', '2026-04-20 10:02:00'),
    (3, 1, 'NORMAL', '2026-04-23 08:00:00', '2026-04-23 12:00:00', NULL, 60, 60, 'OPEN', '2026-04-20 10:03:00', '2026-04-20 10:03:00');

ALTER TABLE sys_user AUTO_INCREMENT = 10;
ALTER TABLE sys_dict_type AUTO_INCREMENT = 10;
ALTER TABLE sys_dict_data AUTO_INCREMENT = 20;
ALTER TABLE police_station AUTO_INCREMENT = 10;
ALTER TABLE duty_module AUTO_INCREMENT = 10;
ALTER TABLE station_module_config AUTO_INCREMENT = 20;
ALTER TABLE police_officer AUTO_INCREMENT = 100;
ALTER TABLE efficiency_factor AUTO_INCREMENT = 20;
ALTER TABLE duty_slot AUTO_INCREMENT = 20;
ALTER TABLE allocation_assignment AUTO_INCREMENT = 20;
ALTER TABLE allocation_reminder_task AUTO_INCREMENT = 10;
ALTER TABLE message_outbox AUTO_INCREMENT = 10;
ALTER TABLE user_notification AUTO_INCREMENT = 10;
ALTER TABLE exam_batch AUTO_INCREMENT = 10;
ALTER TABLE exam_slot AUTO_INCREMENT = 20;
ALTER TABLE exam_appointment AUTO_INCREMENT = 100;
ALTER TABLE exam_appointment_request AUTO_INCREMENT = 100;
ALTER TABLE exam_result AUTO_INCREMENT = 100;
ALTER TABLE exam_waiting AUTO_INCREMENT = 100;
ALTER TABLE recalculation_request AUTO_INCREMENT = 100;
ALTER TABLE recalculation_shard AUTO_INCREMENT = 100;
ALTER TABLE efficiency_result AUTO_INCREMENT = 1000;
