package com.degel.product.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;

@Data
@EqualsAndHashCode(callSuper = true)
public class SpuUpdateVo extends SpuCreateVo {

    @NotNull(message = "商品ID不能为空")
    private Long id;
}
