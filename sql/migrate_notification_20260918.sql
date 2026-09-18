-- C 端站内信：发货/售后审核/仲裁结果通知（商家端靠工作台待办数，不建站内信）
-- 表放 degel_order：写入方（发货/售后钩子）全在本服务进程内，免跨服务写；
-- C 端查询经 degel-app Feign inner 转发
-- 幂等：CREATE TABLE IF NOT EXISTS

USE degel_order;

CREATE TABLE IF NOT EXISTS notification (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT NOT NULL COMMENT 'mall_user.id',
  type        VARCHAR(32) NOT NULL COMMENT 'ship=发货 aftersale=售后结果 arbitrate=仲裁结果',
  title       VARCHAR(128) NOT NULL,
  content     VARCHAR(500) NOT NULL,
  is_read     TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag    TINYINT NOT NULL DEFAULT 0,
  KEY idx_user_read (user_id, is_read, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='C 端站内信';

SHOW TABLES LIKE 'notification';
