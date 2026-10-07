package com.degel.admin.flow.service;

import com.degel.admin.entity.SysUser;
import com.degel.admin.flow.vo.FlowDiagramVo;
import com.degel.admin.service.ISysUserService;
import com.degel.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用流程进度图查询（与具体业务无关）。调用方负责先校验当前用户能否查看该流程实例。
 */
@Service
@RequiredArgsConstructor
public class FlowDiagramService {

    private static final String TYPE_SEQUENCE_FLOW = "sequenceFlow";

    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final ISysUserService userService;

    public FlowDiagramVo getDiagram(String processInstanceId) {
        HistoricProcessInstance instance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance == null) {
            throw new BusinessException("流程实例不存在");
        }
        ProcessDefinition definition = repositoryService.getProcessDefinition(instance.getProcessDefinitionId());

        FlowDiagramVo vo = new FlowDiagramVo();
        vo.setProcessInstanceId(processInstanceId);
        vo.setProcessName(definition.getName());
        vo.setBpmnXml(readResource(definition));
        vo.setDeleteReason(instance.getDeleteReason());
        if (instance.getEndTime() == null) {
            vo.setState("running");
        } else {
            vo.setState(StringUtils.hasText(instance.getDeleteReason()) ? "terminated" : "completed");
        }

        List<HistoricActivityInstance> activities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .list();
        // 同一事务内的节点 start_time 常落在同一毫秒，只按时间排会乱序（撤回单出现"平台审核"排在"店铺提交"前），
        // 再按引擎记录的事务内顺序号兜底
        activities.sort(Comparator.comparing(HistoricActivityInstance::getStartTime)
                .thenComparing(HistoricActivityInstance::getTransactionOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        Map<String, String> userNames = new HashMap<>(4);
        for (HistoricActivityInstance a : activities) {
            if (TYPE_SEQUENCE_FLOW.equals(a.getActivityType())) {
                vo.getCompletedFlowIds().add(a.getActivityId());
                continue;
            }
            // 未结束（待办），或被终止时所在的节点（end_time 有值但带 deleteReason）都算"当前节点"
            boolean active = a.getEndTime() == null || StringUtils.hasText(a.getDeleteReason());
            if (active) {
                vo.getActiveActivityIds().add(a.getActivityId());
            } else {
                vo.getCompletedActivityIds().add(a.getActivityId());
            }
            FlowDiagramVo.Step step = new FlowDiagramVo.Step();
            step.setActivityId(a.getActivityId());
            step.setActivityName(a.getActivityName());
            step.setActivityType(a.getActivityType());
            step.setAssigneeName(resolveUserName(a.getAssignee(), userNames));
            step.setStartTime(a.getStartTime());
            step.setEndTime(a.getEndTime());
            step.setDeleteReason(a.getDeleteReason());
            vo.getSteps().add(step);
        }
        return vo;
    }

    private String readResource(ProcessDefinition definition) {
        try (InputStream in = repositoryService.getResourceAsStream(
                definition.getDeploymentId(), definition.getResourceName())) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("读取流程定义失败");
        }
    }

    /** assignee 存的是 sys_user.id，转成昵称展示；查不到时原样返回 */
    private String resolveUserName(String assignee, Map<String, String> cache) {
        if (!StringUtils.hasText(assignee)) {
            return null;
        }
        return cache.computeIfAbsent(assignee, id -> {
            try {
                SysUser user = userService.getById(Long.valueOf(id));
                if (user == null) {
                    return id;
                }
                return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
            } catch (NumberFormatException e) {
                return id;
            }
        });
    }
}
