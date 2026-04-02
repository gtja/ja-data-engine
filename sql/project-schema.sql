-- JA-UAV-Data-Engine 当前项目业务表结构汇总
-- 说明：
-- 1. 本文件汇总当前项目新增的业务表。
-- 2. iot_device 为现有基础表，这里不重复创建。

-- 异步导出任务表
CREATE TABLE IF NOT EXISTS `async_export_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_no` VARCHAR(64) NOT NULL COMMENT '任务编号',
  `export_type` VARCHAR(64) NOT NULL COMMENT '导出类型',
  `task_status` TINYINT NOT NULL DEFAULT 0 COMMENT '任务状态：0待处理，1执行中，2成功，3失败',
  `file_name` VARCHAR(255) DEFAULT NULL COMMENT '文件名',
  `bucket_name` VARCHAR(128) DEFAULT NULL COMMENT 'S3 bucket',
  `object_key` VARCHAR(512) DEFAULT NULL COMMENT 'S3 对象 key',
  `request_payload` LONGTEXT COMMENT '请求参数 JSON',
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
  `gmt_start` DATETIME DEFAULT NULL COMMENT '开始时间',
  `gmt_finish` DATETIME DEFAULT NULL COMMENT '完成时间',
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `gmt_create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `gmt_modified_by` VARCHAR(64) DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_async_export_task_no` (`task_no`),
  KEY `idx_async_export_task_type_status` (`export_type`, `task_status`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步导出任务表';

-- 设备飞行架次边界状态表
-- total_flight_* 存储“上次已完成架次”的累计值，用于在架次变化时做差分。
CREATE TABLE IF NOT EXISTS `iot_device_flight_state` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_key` VARCHAR(64) NOT NULL COMMENT '产品Key',
  `device_id` VARCHAR(64) NOT NULL COMMENT '设备ID',
  `total_flight_sorties` BIGINT NOT NULL DEFAULT 0 COMMENT '累计飞行架次',
  `total_flight_time` BIGINT NOT NULL DEFAULT 0 COMMENT '累计飞行时长(秒)',
  `total_flight_distance` DECIMAL(20,3) NOT NULL DEFAULT 0 COMMENT '累计飞行里程(米)',
  `last_report_timestamp` BIGINT DEFAULT NULL COMMENT '最近一次上报时间戳(毫秒)',
  `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `gmt_create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `gmt_modified_by` VARCHAR(64) DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_id` (`device_id`),
  KEY `idx_product_key` (`product_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备飞行架次边界状态表';

-- 设备飞行架次明细表
-- 当 totalFlightSorties 变化时，将“当前累计值 - 上次边界累计值”作为上一架次飞行结果写入本表。
CREATE TABLE IF NOT EXISTS `iot_device_flight_sortie` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_key` VARCHAR(64) NOT NULL COMMENT '产品Key',
  `device_id` VARCHAR(64) NOT NULL COMMENT '设备ID',
  `sortie_no` BIGINT NOT NULL COMMENT '架次号(累计值)',
  `flight_time` BIGINT NOT NULL DEFAULT 0 COMMENT '该架次飞行时长(秒)',
  `flight_distance` DECIMAL(20,3) NOT NULL DEFAULT 0 COMMENT '该架次飞行里程(米)',
  `start_total_flight_time` BIGINT NOT NULL DEFAULT 0 COMMENT '该架次开始时累计飞行时长(秒)',
  `end_total_flight_time` BIGINT NOT NULL DEFAULT 0 COMMENT '该架次结束时累计飞行时长(秒)',
  `start_total_flight_distance` DECIMAL(20,3) NOT NULL DEFAULT 0 COMMENT '该架次开始时累计飞行里程(米)',
  `end_total_flight_distance` DECIMAL(20,3) NOT NULL DEFAULT 0 COMMENT '该架次结束时累计飞行里程(米)',
  `report_timestamp` BIGINT DEFAULT NULL COMMENT '设备上报时间戳(毫秒)',
  `settlement_status` TINYINT NOT NULL DEFAULT 0 COMMENT '结算状态：0未结算，1已结算',
  `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `gmt_create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `gmt_modified_by` VARCHAR(64) DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_sortie` (`device_id`, `sortie_no`),
  KEY `idx_device_report_time` (`device_id`, `report_timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备飞行架次明细表';
