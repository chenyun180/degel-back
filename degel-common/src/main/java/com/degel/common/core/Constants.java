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

    /** 角色标识 - 店长 */
    public static final String ROLE_KEY_SHOP_ADMIN = "shop_admin";

    /** 角色标识 - 店员 */
    public static final String ROLE_KEY_SHOP_STAFF = "shop_staff";

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
