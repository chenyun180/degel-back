package com.degel.product.controller;

import com.degel.common.core.R;
import com.degel.product.entity.ProductCategory;
import com.degel.product.service.IProductCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
public class ProductCategoryController {

    private final IProductCategoryService categoryService;

    @GetMapping("/tree")
    public R<List<ProductCategory>> tree() {
        return R.ok(categoryService.listTree());
    }

    @GetMapping("/list")
    public R<List<ProductCategory>> list(
            @RequestParam(value = "parentId", defaultValue = "0") Long parentId) {
        return R.ok(categoryService.listByParentId(parentId));
    }

    @GetMapping("/{id}")
    public R<ProductCategory> getById(@PathVariable Long id) {
        return R.ok(categoryService.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody ProductCategory category) {
        categoryService.createCategory(category);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody ProductCategory category) {
        categoryService.updateCategory(category);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return R.ok();
    }
}
