package com.degel.admin.flow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 流程进度图数据：BPMN 定义（实例实际所用版本）+ 运行轨迹，前端 bpmn-js 渲染并高亮。
 */
@Data
public class FlowDiagramVo {

    private String processInstanceId;
    private String processName;
    /** 实例启动时所用版本的 BPMN XML（流程改版后旧实例仍按旧图展示） */
    private String bpmnXml;
    /** running=进行中 / completed=正常结束 / terminated=被终止（如店铺撤回） */
    private String state;
    private String deleteReason;
    /** 已完成的节点 id（不含连线） */
    private List<String> completedActivityIds = new ArrayList<>();
    /** 当前停留的节点 id（进行中=待办；被终止=终止时所在节点） */
    private List<String> activeActivityIds = new ArrayList<>();
    /** 已走过的连线 id */
    private List<String> completedFlowIds = new ArrayList<>();
    /** 节点轨迹（按发生顺序，不含连线），用于时间线 */
    private List<Step> steps = new ArrayList<>();

    @Data
    public static class Step {
        private String activityId;
        private String activityName;
        private String activityType;
        private String assigneeName;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private Date startTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private Date endTime;
        private String deleteReason;
    }
}
