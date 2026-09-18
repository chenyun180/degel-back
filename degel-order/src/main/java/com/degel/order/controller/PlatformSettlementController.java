package com.degel.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.common.core.R;
import com.degel.order.entity.SettlementWithdraw;
import com.degel.order.service.ISettlementService;
import com.degel.order.service.ISettlementWithdrawService;
import com.degel.order.vo.SettlementConfigVo;
import com.degel.order.vo.WithdrawAuditVo;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 平台结算管理（提现审核 + 佣金配置）
 * 鉴权：网关 degel.security.admin-urls 的 "*:/order/platform/settlement" 限定平台管理员
 */
@RestController
@RequestMapping("/platform/settlement")
@RequiredArgsConstructor
public class PlatformSettlementController {

    private final ISettlementService settlementService;
    private final ISettlementWithdrawService withdrawService;

    /** 提现审核列表（platform 全量：shopId 不传 = 全部店铺） */
    @GetMapping("/withdraw/page")
    public R<IPage<SettlementWithdraw>> withdrawPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Integer status) {
        return R.ok(withdrawService.page(new Page<>(current, size), shopId, status));
    }

    /** 审核：approve=通过并模拟打款 / 驳回（必填备注）。审核人取网关注入的 X-User-Name */
    @PutMapping("/withdraw/audit")
    public R<Void> audit(@Validated @RequestBody WithdrawAuditVo vo,
                         @RequestHeader(value = "X-User-Name", defaultValue = "platform") String auditBy) {
        withdrawService.audit(vo, auditBy);
        return R.ok();
    }

    @GetMapping("/config")
    public R<SettlementConfigVo> config() {
        return R.ok(settlementService.getConfig());
    }

    /** 资金总览：佣金/补贴/提现/欠款/净现金流聚合 */
    @GetMapping("/overview")
    public R<com.degel.order.vo.PlatformSettlementOverviewVo> overview() {
        return R.ok(settlementService.getPlatformOverview());
    }

    /** 修改配置：佣金比例（仅影响之后新生成的结算明细）+ 售后窗口（即时生效），字段传 null 表示不修改 */
    @PutMapping("/config")
    public R<Void> updateConfig(@RequestBody SettlementConfigVo vo) {
        if (vo == null || (vo.getCommissionRate() == null && vo.getAftersaleDays() == null)) {
            return R.fail("没有可修改的配置项");
        }
        if (vo.getCommissionRate() != null) {
            settlementService.updateConfig(vo.getCommissionRate());
        }
        if (vo.getAftersaleDays() != null) {
            settlementService.updateAftersaleDays(vo.getAftersaleDays());
        }
        return R.ok();
    }
}
