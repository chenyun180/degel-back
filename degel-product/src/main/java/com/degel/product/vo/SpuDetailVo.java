package com.degel.product.vo;

import com.degel.product.entity.ProductSku;
import com.degel.product.entity.ProductSpu;
import lombok.Data;

import java.util.List;

@Data
public class SpuDetailVo {

    private ProductSpu spu;
    private List<ProductSku> skuList;
}
