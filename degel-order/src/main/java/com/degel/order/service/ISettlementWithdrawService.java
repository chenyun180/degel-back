package com.degel.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.order.entity.SettlementWithdraw;
import com.degel.order.vo.WithdrawAuditVo;
import com.degel.order.vo.WithdrawApplyVo;

/** 商家提现：申请（店铺端）+ 审核（平台端） */
public interface ISettlementWithdrawService {

    /** 商家申请提现（校验余额，申请本身不扣款——审核通过才扣） */
    void apply(Long shopId, WithdrawApplyVo vo);

    /** 平台审核：approve=通过并模拟打款（扣余额+流水）/ false 驳回（无资金动作） */
    void audit(WithdrawAuditVo vo, String auditBy);

    IPage<SettlementWithdraw> page(IPage<SettlementWithdraw> page, Long shopId, Integer status);
}
