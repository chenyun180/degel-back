package com.degel.admin.flow.delegate;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.degel.admin.entity.SysShopChange;
import com.degel.admin.flow.ShopChangeFlow;
import com.degel.admin.mapper.SysShopChangeMapper;
import com.degel.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审批驳回：只标记申请单，不动 sys_shop。
 */
@Component("shopChangeRejectDelegate")
@RequiredArgsConstructor
public class ShopChangeRejectDelegate implements JavaDelegate {

    private final SysShopChangeMapper changeMapper;

    @Override
    public void execute(DelegateExecution execution) {
        Long changeId = (Long) execution.getVariable(ShopChangeFlow.VAR_CHANGE_ID);
        int rows = changeMapper.update(null, new LambdaUpdateWrapper<SysShopChange>()
                .eq(SysShopChange::getId, changeId)
                .eq(SysShopChange::getStatus, SysShopChange.STATUS_PENDING)
                .set(SysShopChange::getStatus, SysShopChange.STATUS_REJECTED)
                .set(SysShopChange::getAuditUserId, execution.getVariable(ShopChangeFlow.VAR_AUDIT_USER_ID))
                .set(SysShopChange::getAuditRemark, execution.getVariable(ShopChangeFlow.VAR_AUDIT_REMARK))
                .set(SysShopChange::getAuditTime, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException("该申请已被处理，请刷新");
        }
    }
}
