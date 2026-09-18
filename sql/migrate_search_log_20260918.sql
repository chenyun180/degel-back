-- C 端搜索埋点表（搜索词分析数据源）
-- 2026-09-18：本期只做埋点落库积累数据，报表等数据积累后再做
-- 写入方：degel-app getProductList（仅 C 端，管理端筛选不经过此埋点）
-- 口径：仅第一页记录（翻页不重复计）；空结果也记（result_count=0 是缺货缺口信号）
USE degel_product;

CREATE TABLE IF NOT EXISTS `product_search_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `keyword` VARCHAR(64) NOT NULL COMMENT '清洗后关键词（复用热搜词清洗规则：trim/压空白/限长30）',
  `user_id` BIGINT DEFAULT NULL COMMENT '登录用户 id，匿名搜索为 NULL',
  `result_count` INT NOT NULL DEFAULT 0 COMMENT '搜索结果总数（0=未搜到）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_keyword` (`keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='C 端搜索埋点';
