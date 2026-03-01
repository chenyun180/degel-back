package com.degel.product.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SpuCreateVo {

    @NotNull(message = "类目不能为空")
    private Long categoryId;

    @NotBlank(message = "商品名称不能为空")
    private String name;

    private String subtitle;
    private String description;
    private String detailContent;
    private String mainImage;
    private String images;
    private String keyword;

    @NotNull(message = "至少需要一个SKU")
    @Size(min = 1, message = "至少需要一个SKU")
    private List<SkuItem> skuList;

    @Data
    public static class SkuItem {
        private String skuCode;
        private String specData;
        @NotNull(message = "价格不能为空")
        private BigDecimal price;
        private BigDecimal originalPrice;
        private BigDecimal costPrice;
        @NotNull(message = "库存不能为空")
        private Integer stock;
        private Integer stockWarning;
        private BigDecimal weight;
        private String image;
    }
}
