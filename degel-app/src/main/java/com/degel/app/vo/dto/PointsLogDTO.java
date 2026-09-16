package com.degel.app.vo.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 积分明细（与 marketing PointsLogVO 对齐的 BFF 侧副本；records 为分页字段） */
@Data
public class PointsLogDTO {

    private Long id;
    private String type;
    private Integer points;
    private Long orderId;
    private String orderNo;
    private String remark;
    private LocalDateTime createTime;

    /** 分页包装（与 marketing IPage JSON 对齐） */
    @Data
    public static class Page {
        private List<PointsLogDTO> records;
        private Long total;
        private Long current;
        private Long size;
    }
}
