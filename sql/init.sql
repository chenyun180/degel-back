CREATE DATABASE IF NOT EXISTS degel_admin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE degel_admin;

-- ========== 系统用户表 ==========
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(50)  NOT NULL,
    password    VARCHAR(100) NOT NULL,
    nickname    VARCHAR(50)  DEFAULT '',
    phone       VARCHAR(20)  DEFAULT '',
    email       VARCHAR(50)  DEFAULT '',
    avatar      VARCHAR(255) DEFAULT '',
    status      TINYINT      DEFAULT 0 COMMENT '0=正常 1=停用',
    shop_id     BIGINT       DEFAULT 0 COMMENT '所属店铺ID，0=平台',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag    TINYINT      DEFAULT 0 COMMENT '0=未删除 1=已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ========== 系统角色表 ==========
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    role_name   VARCHAR(50) NOT NULL,
    role_key    VARCHAR(50) NOT NULL,
    sort        INT         DEFAULT 0,
    status      TINYINT     DEFAULT 0 COMMENT '0=正常 1=停用',
    remark      VARCHAR(255) DEFAULT '',
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag    TINYINT     DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_key (role_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- ========== 系统菜单表 ==========
CREATE TABLE IF NOT EXISTS sys_menu (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id   BIGINT       DEFAULT 0 COMMENT '父菜单ID，0=顶级',
    menu_name   VARCHAR(50)  NOT NULL,
    path        VARCHAR(200) DEFAULT '',
    component   VARCHAR(200) DEFAULT '',
    perms       VARCHAR(100) DEFAULT '' COMMENT '权限标识',
    icon        VARCHAR(100) DEFAULT '',
    menu_type   CHAR(1)      NOT NULL COMMENT 'M=目录 C=菜单 F=按钮',
    sort        INT          DEFAULT 0,
    visible     TINYINT      DEFAULT 0 COMMENT '0=显示 1=隐藏',
    status      TINYINT      DEFAULT 0 COMMENT '0=正常 1=停用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag    TINYINT      DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统菜单表';

-- ========== 用户-角色关联表 ==========
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- ========== 角色-菜单关联表 ==========
CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

-- ========== 店铺表 ==========
CREATE TABLE IF NOT EXISTS sys_shop (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    shop_name     VARCHAR(100) NOT NULL,
    contact_name  VARCHAR(50)  DEFAULT '',
    contact_phone VARCHAR(20)  DEFAULT '',
    status        TINYINT      DEFAULT 0 COMMENT '0=正常 1=停用',
    expire_time   DATETIME     DEFAULT NULL COMMENT '有效期',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag      TINYINT      DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺表';
