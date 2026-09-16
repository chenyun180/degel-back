package com.degel.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 积分账户（mk_points_account）。余额冗余，源为 mk_points_log 流水。
 * 与 mk_banner 不同：自增 id 即可（不出参给前端 JS，无精度问题）。
 */
@Data
@TableName("mk_points_account")
public class MkPointsAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** C 端用户 ID */
    private Long userId;

    /** 积分余额 */
    private Integer balance;

    private LocalDateTime updateTime;
}
