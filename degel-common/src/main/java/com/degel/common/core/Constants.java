package com.degel.common.core;

public class Constants {

    /** 正常状态 */
    public static final int STATUS_NORMAL = 0;

    /** 停用状态 */
    public static final int STATUS_DISABLED = 1;

    /** 菜单类型 - 目录 */
    public static final String MENU_TYPE_DIR = "M";

    /** 菜单类型 - 菜单 */
    public static final String MENU_TYPE_MENU = "C";

    /** 菜单类型 - 按钮 */
    public static final String MENU_TYPE_BUTTON = "F";

    /** 平台 shopId */
    public static final long PLATFORM_SHOP_ID = 0L;

    /** 角色类型 - 平台 */
    public static final String ROLE_TYPE_PLATFORM = "platform";

    /** 角色类型 - 店铺 */
    public static final String ROLE_TYPE_SHOP = "shop";

    /** 角色标识 - 店铺（每个店铺只有一个角色） */
    public static final String ROLE_KEY_SHOP = "shop";

    /** 角色标识 - 超级管理员 */
    public static final String ROLE_KEY_ADMIN = "admin";

    /** 管理端 token 黑名单 key 前缀（degel-auth 写入，网关校验；值为 jti） */
    public static final String AUTH_BLACKLIST_PREFIX = "auth:blacklist:";

    /**
     * 管理端用户级 token 版本 key 前缀（auth:tokenver:{userId}）。
     * 签发时读当前值写入 token_version claim；网关每请求比对 claim >= 当前值。
     * 改密/禁用/删除用户/停店铺时 INCR 该 key，使该用户全部已签发 token 立即失效。
     */
    public static final String AUTH_TOKEN_VERSION_PREFIX = "auth:tokenver:";

    /** 用户默认密码 */
    public static final String DEFAULT_PASSWORD = "admin123";

    /** 审核状态 - 草稿 */
    public static final int AUDIT_DRAFT = 0;

    /** 审核状态 - 待审核 */
    public static final int AUDIT_PENDING = 1;

    /** 审核状态 - 审核通过 */
    public static final int AUDIT_APPROVED = 2;

    /** 审核状态 - 已驳回 */
    public static final int AUDIT_REJECTED = 3;

    private Constants() {
    }
}
