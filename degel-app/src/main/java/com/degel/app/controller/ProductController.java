package com.degel.app.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.service.ProductService;
import com.degel.app.vo.AppSpuDetailVO;
import com.degel.app.vo.AppSpuListVO;
import com.degel.app.vo.CategoryTreeVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品浏览 Controller（无需登录）
 */
@RestController
@RequestMapping("/app/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * B-02：获取商品分类树
     * GET /app/product/category/tree
     */
    @GetMapping("/category/tree")
    public R<List<CategoryTreeVO>> getCategoryTree() {
        return R.ok(productService.getCategoryTree());
    }

    /**
     * B-03：分页查询商品列表
     * GET /app/product/list
     */
    @GetMapping("/list")
    public R<IPage<AppSpuListVO>> getProductList(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize
    ) {
        // pageSize 最大限制 50
        if (pageSize > 50) {
            pageSize = 50;
        }
        return R.ok(productService.getProductList(categoryId, keyword, page, pageSize));
    }

    /**
     * B-04：获取商品详情
     * GET /app/product/{spuId}
     */
    @GetMapping("/{spuId}")
    public R<AppSpuDetailVO> getProductDetail(@PathVariable Long spuId) {
        return R.ok(productService.getProductDetail(spuId));
    }
}
