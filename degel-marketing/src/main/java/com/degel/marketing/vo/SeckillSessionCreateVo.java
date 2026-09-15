package com.degel.marketing.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 秒杀场次新增/编辑入参（id 空=新增，非空=编辑）。
 */
@Data
public class SeckillSessionCreateVo {

    /** 空=新增（MP 自动雪花）；非空=编辑 */
    private Long id;

    @NotBlank(message = "场次名称不能为空")
    @Size(max = 64, message = "场次名称不能超过64个字符")
    private String name;

    @NotNull(message = "开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @NotNull(message = "排序不能为空")
    private Integer sort;
}
