package com.degel.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.product.entity.ProductCategory;

import java.util.List;

public interface IProductCategoryService extends IService<ProductCategory> {

    List<ProductCategory> listTree();

    List<ProductCategory> listByParentId(Long parentId);

    void createCategory(ProductCategory category);

    void updateCategory(ProductCategory category);

    void deleteCategory(Long id);
}
