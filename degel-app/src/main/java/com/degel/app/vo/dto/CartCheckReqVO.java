package com.degel.app.vo.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 预览结算请求 VO
 */
@Data
public class CartCheckReqVO {

    /**
     * 勾选的购物车 ID 列表
     */
    @NotEmpty(message = "请选择商品")
    private List<Long> cartIds;
}
