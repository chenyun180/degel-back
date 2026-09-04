package com.degel.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.product.entity.ProductCategory;

import java.util.List;

public interface IProductCategoryService extends IService<ProductCategory> {

    List<ProductCategory> listTree();

    /**
     * 收集类目祖先链 id（含自身，从当前类目向根回溯），ES 索引展开用。
     * 与 pageSpu 的 collectDescendantIds（向下展开子孙）方向相反：索引时算好祖先，
     * 查询端选任意层级类目一次 term 即命中。
     */
    List<Long> collectAncestorIds(Long categoryId);

    List<ProductCategory> listByParentId(Long parentId);

    void createCategory(ProductCategory category);

    void updateCategory(ProductCategory category);

    void deleteCategory(Long id);
}
