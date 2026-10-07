package com.degel.admin.flow.delegate;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.degel.admin.entity.SysShopChange;
import com.degel.admin.flow.ShopChangeFlow;
import com.degel.admin.mapper.SysShopChangeMapper;
import com.degel.admin.service.ISysShopService;
import com.degel.admin.vo.ShopProfileVo;
import com.degel.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 审批通过：申请资料整体回写 sys_shop，申请单置为已通过。
 * 与 taskService.complete 处于同一 Spring 事务（Flowable 与 mybatis-plus 共用数据源/事务管理器），任一步失败整体回滚。
 */
@Component("shopChangeApproveDelegate")
@RequiredArgsConstructor
public class ShopChangeApproveDelegate implements JavaDelegate {

    private final SysShopChangeMapper changeMapper;
    private final ISysShopService shopService;
    private final ObjectMapper objectMapper;

    @Override
    public void execute(DelegateExecution execution) {
        Long changeId = (Long) execution.getVariable(ShopChangeFlow.VAR_CHANGE_ID);
        SysShopChange change = changeMapper.selectById(changeId);
        if (change == null) {
            throw new BusinessException("变更申请不存在：" + changeId);
        }
        ShopProfileVo after;
        try {
            after = objectMapper.readValue(change.getAfterData(), ShopProfileVo.class);
        } catch (IOException e) {
            throw new BusinessException("变更申请数据损坏：" + changeId);
        }
        shopService.updateById(after.toShop(change.getShopId()));

        int rows = changeMapper.update(null, new LambdaUpdateWrapper<SysShopChange>()
                .eq(SysShopChange::getId, changeId)
                .eq(SysShopChange::getStatus, SysShopChange.STATUS_PENDING)
                .set(SysShopChange::getStatus, SysShopChange.STATUS_APPROVED)
                .set(SysShopChange::getAuditUserId, execution.getVariable(ShopChangeFlow.VAR_AUDIT_USER_ID))
                .set(SysShopChange::getAuditRemark, execution.getVariable(ShopChangeFlow.VAR_AUDIT_REMARK))
                .set(SysShopChange::getAuditTime, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException("该申请已被处理，请刷新");
        }
    }
}
