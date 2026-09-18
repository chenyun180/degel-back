package com.degel.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * C 端搜索埋点（搜索词分析数据源）。
 * 只写不改不删，无 del_flag（不是业务实体）；量大用自增 id（无跨服务引用）。
 */
@Data
@TableName("product_search_log")
public class ProductSearchLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 清洗后关键词（清洗规则收敛在 degel-app 侧，与热搜词共用） */
    private String keyword;

    /** 登录用户 id，匿名搜索为 null */
    private Long userId;

    /** 搜索结果总数（0=未搜到，缺货缺口信号） */
    private Integer resultCount;

    private LocalDateTime createTime;
}
