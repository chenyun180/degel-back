package com.degel.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.common.core.exception.BusinessException;
import com.degel.product.entity.ProductCategory;
import com.degel.product.mapper.ProductCategoryMapper;
import com.degel.product.service.IProductCategoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductCategoryServiceImpl extends ServiceImpl<ProductCategoryMapper, ProductCategory>
        implements IProductCategoryService {

    @Override
    public List<ProductCategory> listTree() {
        List<ProductCategory> all = list(new LambdaQueryWrapper<ProductCategory>()
                .orderByAsc(ProductCategory::getSort));

        Map<Long, List<ProductCategory>> grouped = all.stream()
                .collect(Collectors.groupingBy(ProductCategory::getParentId));

        List<ProductCategory> roots = grouped.getOrDefault(0L, new ArrayList<>());
        roots.forEach(root -> buildChildren(root, grouped));
        return roots;
    }

    @Override
    public List<ProductCategory> listByParentId(Long parentId) {
        return list(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getParentId, parentId)
                .orderByAsc(ProductCategory::getSort));
    }

    @Override
    public void createCategory(ProductCategory category) {
        if (category.getParentId() == null) {
            category.setParentId(0L);
        }
        save(category);
    }

    @Override
    public void updateCategory(ProductCategory category) {
        if (category.getId() == null) {
            throw new BusinessException("类目ID不能为空");
        }
        updateById(category);
    }

    @Override
    public void deleteCategory(Long id) {
        long childCount = count(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException("该类目下存在子类目，无法删除");
        }
        removeById(id);
    }

    private void buildChildren(ProductCategory parent, Map<Long, List<ProductCategory>> grouped) {
        List<ProductCategory> children = grouped.getOrDefault(parent.getId(), new ArrayList<>());
        parent.setChildren(children);
        children.forEach(child -> buildChildren(child, grouped));
    }
}
