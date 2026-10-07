package com.degel.admin.flow;

/**
 * 流程 shopChange 的 key 与变量名约定（与 resources/processes/shop-change.bpmn20.xml 对应）。
 */
public final class ShopChangeFlow {

    public static final String PROCESS_KEY = "shopChange";
    public static final String BUSINESS_KEY_PREFIX = "shop_change:";

    public static final String VAR_CHANGE_ID = "changeId";
    public static final String VAR_SHOP_ID = "shopId";
    public static final String VAR_APPROVED = "approved";
    public static final String VAR_AUDIT_USER_ID = "auditUserId";
    public static final String VAR_AUDIT_REMARK = "auditRemark";

    private ShopChangeFlow() {
    }
}
