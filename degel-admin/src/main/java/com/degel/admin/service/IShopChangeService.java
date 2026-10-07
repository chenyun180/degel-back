package com.degel.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.admin.entity.SysShopChange;
import com.degel.admin.vo.ShopProfileVo;

public interface IShopChangeService extends IService<SysShopChange> {

    /** 店铺提交资料变更申请并发起审批流程；同一店铺同时只允许一条待审核申请 */
    SysShopChange submit(Long shopId, Long userId, ShopProfileVo profile);

    /** 店铺撤回本店待审核申请（终止流程实例） */
    void withdraw(Long changeId, Long shopId);

    /** 平台审批：办理流程中的「平台审核」任务，结果由流程内 delegate 回写 */
    void audit(Long changeId, Long userId, boolean approved, String remark);

    /** 本店最近一条申请（店铺信息页展示审核状态），无则 null */
    SysShopChange latestOfShop(Long shopId);

    IPage<SysShopChange> pageChanges(IPage<SysShopChange> page, Integer status, String shopName);
}
