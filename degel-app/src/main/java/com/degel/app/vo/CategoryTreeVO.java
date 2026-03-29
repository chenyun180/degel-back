package com.degel.app.vo;

import lombok.Data;

import java.util.List;

/**
 * 商品分类树 VO
 */
@Data
public class CategoryTreeVO {

    private Long id;

    private String name;

    private String icon;

    private Integer sort;

    private List<CategoryTreeVO> children;
}
