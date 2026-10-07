package com.degel.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.admin.entity.SysShopChange;
import com.degel.admin.flow.service.FlowDiagramService;
import com.degel.admin.flow.vo.FlowDiagramVo;
import com.degel.admin.service.IShopChangeService;
import com.degel.admin.vo.ShopChangeAuditVo;
import com.degel.admin.vo.ShopProfileVo;
import com.degel.common.core.R;
import com.degel.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 店铺资料变更审批（Flowable 流程 shopChange）。
 * 网关鉴权依赖路径前缀：/shop/mine/** 命中 admin-url-excludes（店铺自服务），/shop/change/** 命中 admin-urls（仅超管）。
 */
@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopChangeController {

    private final IShopChangeService changeService;
    private final FlowDiagramService diagramService;

    // ==================== 店铺侧 ====================

    @PostMapping("/mine/change")
    public R<SysShopChange> submit(@Validated @RequestBody ShopProfileVo profile,
                                   @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId,
                                   @RequestHeader("X-User-Id") Long userId) {
        requireShop(shopId);
        return R.ok(changeService.submit(shopId, userId, profile));
    }

    @GetMapping("/mine/change/latest")
    public R<SysShopChange> latest(@RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        requireShop(shopId);
        return R.ok(changeService.latestOfShop(shopId));
    }

    @PutMapping("/mine/change/{id}/withdraw")
    public R<Void> withdraw(@PathVariable Long id,
                            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        requireShop(shopId);
        changeService.withdraw(id, shopId);
        return R.ok();
    }

    /** 本店申请的审批进度图；只能看本店的 */
    @GetMapping("/mine/change/{id}/diagram")
    public R<FlowDiagramVo> mineDiagram(@PathVariable Long id,
                                        @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        requireShop(shopId);
        SysShopChange change = changeService.getById(id);
        if (change == null || !change.getShopId().equals(shopId)) {
            throw new BusinessException("申请不存在");
        }
        return R.ok(diagramService.getDiagram(change.getProcessInstanceId()));
    }

    // ==================== 平台侧 ====================

    @GetMapping("/change/page")
    public R<IPage<SysShopChange>> page(@RequestParam(defaultValue = "1") Integer current,
                                        @RequestParam(defaultValue = "10") Integer size,
                                        @RequestParam(required = false) Integer status,
                                        @RequestParam(required = false) String shopName) {
        return R.ok(changeService.pageChanges(new Page<>(current, size), status, shopName));
    }

    @GetMapping("/change/{id}/diagram")
    public R<FlowDiagramVo> diagram(@PathVariable Long id) {
        SysShopChange change = changeService.getById(id);
        if (change == null) {
            throw new BusinessException("申请不存在");
        }
        return R.ok(diagramService.getDiagram(change.getProcessInstanceId()));
    }

    @PutMapping("/change/{id}/audit")
    public R<Void> audit(@PathVariable Long id,
                         @Validated @RequestBody ShopChangeAuditVo vo,
                         @RequestHeader("X-User-Id") Long userId) {
        changeService.audit(id, userId, vo.getApproved(), vo.getRemark());
        return R.ok();
    }

    private void requireShop(Long shopId) {
        if (shopId == 0) {
            throw new BusinessException("平台账号无店铺信息");
        }
    }
}
