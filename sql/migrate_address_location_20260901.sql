-- 迁移：mall_address 增加经纬度字段（高德地图选点，GCJ-02 火星坐标系）
-- 日期：2026-09-01
-- 说明：字段可空，存量手填地址无坐标不影响；新地址经 H5 高德地图选点后写入

USE degel_app;

ALTER TABLE mall_address
    ADD COLUMN longitude DECIMAL(9,6) DEFAULT NULL COMMENT '经度（GCJ-02 火星坐标系，高德地图选点）' AFTER detail,
    ADD COLUMN latitude  DECIMAL(8,6) DEFAULT NULL COMMENT '纬度（GCJ-02 火星坐标系，高德地图选点）' AFTER longitude;
