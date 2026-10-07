package com.degel.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.admin.entity.SysShop;
import com.degel.admin.entity.SysShopChange;
import com.degel.admin.flow.ShopChangeFlow;
import com.degel.admin.mapper.SysShopChangeMapper;
import com.degel.admin.service.IShopChangeService;
import com.degel.admin.service.ISysShopService;
import com.degel.admin.service.ISysUserService;
import com.degel.admin.vo.ShopProfileVo;
import com.degel.common.core.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShopChangeServiceImpl extends ServiceImpl<SysShopChangeMapper, SysShopChange> implements IShopChangeService {

    private final ISysShopService shopService;
    private final ISysUserService userService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysShopChange submit(Long shopId, Long userId, ShopProfileVo profile) {
        SysShop shop = shopService.getById(shopId);
        if (shop == null) {
            throw new BusinessException("店铺不存在");
        }
        long pending = this.count(new LambdaQueryWrapper<SysShopChange>()
                .eq(SysShopChange::getShopId, shopId)
                .eq(SysShopChange::getStatus, SysShopChange.STATUS_PENDING));
        if (pending > 0) {
            throw new BusinessException("已有待审核的变更申请，请等待审核或先撤回");
        }
        ShopProfileVo before = ShopProfileVo.of(shop);
        if (before.sameAs(profile)) {
            throw new BusinessException("资料未发生变化");
        }

        SysShopChange change = new SysShopChange();
        change.setShopId(shopId);
        change.setShopName(shop.getShopName());
        change.setBeforeData(toJson(before));
        change.setAfterData(toJson(profile));
        change.setStatus(SysShopChange.STATUS_PENDING);
        change.setApplyUserId(userId);
        this.save(change);

        Map<String, Object> vars = new HashMap<>(4);
        vars.put(ShopChangeFlow.VAR_CHANGE_ID, change.getId());
        vars.put(ShopChangeFlow.VAR_SHOP_ID, shopId);
        ProcessInstance instance;
        // initiator（startEvent 的 flowable:initiator）取自线程内认证用户，用完必须清，防止线程复用串号
        Authentication.setAuthenticatedUserId(String.valueOf(userId));
        try {
            instance = runtimeService.startProcessInstanceByKey(
                    ShopChangeFlow.PROCESS_KEY, ShopChangeFlow.BUSINESS_KEY_PREFIX + change.getId(), vars);
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }

        SysShopChange update = new SysShopChange();
        update.setId(change.getId());
        update.setProcessInstanceId(instance.getId());
        this.updateById(update);
        change.setProcessInstanceId(instance.getId());
        return change;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long changeId, Long shopId) {
        SysShopChange change = this.getById(changeId);
        // 店铺只能撤回本店申请；不存在与越权统一提示，不泄露他店申请是否存在
        if (change == null || !change.getShopId().equals(shopId)) {
            throw new BusinessException("申请不存在");
        }
        boolean updated = this.update(new LambdaUpdateWrapper<SysShopChange>()
                .eq(SysShopChange::getId, changeId)
                .eq(SysShopChange::getStatus, SysShopChange.STATUS_PENDING)
                .set(SysShopChange::getStatus, SysShopChange.STATUS_WITHDRAWN));
        if (!updated) {
            throw new BusinessException("申请已被处理，无法撤回");
        }
        runtimeService.deleteProcessInstance(change.getProcessInstanceId(), "店铺撤回");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long changeId, Long userId, boolean approved, String remark) {
        if (!approved && !StringUtils.hasText(remark)) {
            throw new BusinessException("驳回必须填写审批意见");
        }
        SysShopChange change = this.getById(changeId);
        if (change == null) {
            throw new BusinessException("申请不存在");
        }
        if (change.getStatus() != SysShopChange.STATUS_PENDING) {
            throw new BusinessException("该申请已被处理，请刷新");
        }
        // 审批权由流程定义决定：只有 candidateGroups 命中当前用户角色的任务才可办理
        List<String> roleKeys = userService.getRoleKeysByUserId(userId);
        if (roleKeys.isEmpty()) {
            throw new BusinessException("无审批权限");
        }
        Task task = taskService.createTaskQuery()
                .processInstanceId(change.getProcessInstanceId())
                .taskCandidateGroupIn(roleKeys)
                .active()
                .singleResult();
        if (task == null) {
            throw new BusinessException("无审批权限或审批任务不存在");
        }

        Map<String, Object> vars = new HashMap<>(4);
        vars.put(ShopChangeFlow.VAR_APPROVED, approved);
        vars.put(ShopChangeFlow.VAR_AUDIT_USER_ID, userId);
        vars.put(ShopChangeFlow.VAR_AUDIT_REMARK, remark == null ? "" : remark.trim());
        Authentication.setAuthenticatedUserId(String.valueOf(userId));
        try {
            // claim 记录办理人到 ACT_HI_TASKINST.ASSIGNEE_，留审计痕迹
            taskService.claim(task.getId(), String.valueOf(userId));
            taskService.complete(task.getId(), vars);
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }
    }

    @Override
    public SysShopChange latestOfShop(Long shopId) {
        return this.getOne(new LambdaQueryWrapper<SysShopChange>()
                .eq(SysShopChange::getShopId, shopId)
                .orderByDesc(SysShopChange::getId)
                .last("LIMIT 1"));
    }

    @Override
    public IPage<SysShopChange> pageChanges(IPage<SysShopChange> page, Integer status, String shopName) {
        return this.page(page, new LambdaQueryWrapper<SysShopChange>()
                .eq(status != null, SysShopChange::getStatus, status)
                .like(StringUtils.hasText(shopName), SysShopChange::getShopName, shopName)
                .orderByDesc(SysShopChange::getId));
    }

    private String toJson(ShopProfileVo profile) {
        try {
            return objectMapper.writeValueAsString(profile);
        } catch (JsonProcessingException e) {
            throw new BusinessException("资料序列化失败");
        }
    }
}
