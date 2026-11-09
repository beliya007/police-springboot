-- =============================================================================
-- 警力合理配置规划平台 —— 字典表初始化
--
-- 说明：完整建表脚本 police-force-init.sql 已经包含 sys_dict_type /
--      sys_dict_data 的建表与种子数据，本文件仅保留供增量部署使用。
-- =============================================================================

USE police_force;

CREATE TABLE IF NOT EXISTS sys_dict_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dict_name VARCHAR(100) NOT NULL,
    dict_type VARCHAR(100) NOT NULL UNIQUE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_dict_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dict_type VARCHAR(100) NOT NULL,
    dict_label VARCHAR(100) NOT NULL,
    dict_value VARCHAR(100) NOT NULL,
    sort_order INT DEFAULT 0,
    is_default CHAR(1) DEFAULT 'N'
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO sys_dict_type (dict_name, dict_type)
SELECT '警局类型', 'station_type'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type WHERE dict_type = 'station_type'
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, sort_order, is_default)
SELECT 'station_type', '分局', 'DIVISION', 1, 'Y'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data WHERE dict_type = 'station_type' AND dict_value = 'DIVISION'
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, sort_order, is_default)
SELECT 'station_type', '派出所', 'SUB_STATION', 2, 'N'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data WHERE dict_type = 'station_type' AND dict_value = 'SUB_STATION'
);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, sort_order, is_default)
SELECT 'station_type', '警区', 'PRECINCT', 3, 'N'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data WHERE dict_type = 'station_type' AND dict_value = 'PRECINCT'
);
