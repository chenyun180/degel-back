package com.degel.product.vo;

import lombok.Data;

/**
 * 内部接口专用：SPU 主图轻量 VO（评价等场景按 id 批量取图，避免搬整个商品 VO）
 */
@Data
public class SpuImageVo {

    private Long id;

    private String mainImage;
}
