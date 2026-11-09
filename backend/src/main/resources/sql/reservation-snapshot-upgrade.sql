ALTER TABLE allocation
    ADD COLUMN station_name VARCHAR(100) NULL AFTER slot_id,
    ADD COLUMN station_code VARCHAR(50) NULL AFTER station_name,
    ADD COLUMN station_location VARCHAR(255) NULL AFTER station_code,
    ADD COLUMN slot_start_datetime DATETIME NULL AFTER station_location,
    ADD COLUMN slot_end_datetime DATETIME NULL AFTER slot_start_datetime;

UPDATE allocation r
         LEFT JOIN station res ON res.id = r.station_id
         LEFT JOIN duty_slot slot ON slot.id = r.slot_id
SET r.station_name = COALESCE(r.station_name, res.station_name),
    r.station_code = COALESCE(r.station_code, res.station_code),
    r.station_location = COALESCE(r.station_location, res.location),
    r.slot_start_datetime = COALESCE(r.slot_start_datetime, slot.start_datetime),
    r.slot_end_datetime = COALESCE(r.slot_end_datetime, slot.end_datetime);

-- 回填完成后固化预约快照约束，读取历史预约时不再回查当前资源和时段。
ALTER TABLE allocation
    MODIFY COLUMN station_name VARCHAR(100) NOT NULL,
    MODIFY COLUMN station_code VARCHAR(50) NOT NULL,
    MODIFY COLUMN slot_start_datetime DATETIME NOT NULL,
    MODIFY COLUMN slot_end_datetime DATETIME NOT NULL;
