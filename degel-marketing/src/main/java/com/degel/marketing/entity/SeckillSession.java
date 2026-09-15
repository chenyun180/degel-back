package com.degel.marketing.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀场次（mk_seckill_session）。
 *
 * 不继承 BaseEntity：对齐 Banner 先例（裸 Long id + bootstrap 全局 id-type=assign_id → MP 内置雪花）。
 * createTime/updateTime/delFlag 由 DB DEFAULT / ON UPDATE 兜底（MP 非空字段才进 INSERT）。
 */
@Data
@TableName("mk_seckill_session")
public class SeckillSession {

    /** MP ASSIGN_ID 雪花；出参必须 @JsonSerialize(ToStringSerializer) 防 JS 精度丢失 */
    private Long id;

    /** 场次名称，如 "20:00 场" */
    private String name;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 0=停用 1=启用；新增不设置时走 DB DEFAULT 0（默认停用） */
    private Integer status;

    /** 数字小靠前 */
    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
