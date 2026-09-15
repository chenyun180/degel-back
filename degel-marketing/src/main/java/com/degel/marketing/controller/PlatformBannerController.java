package com.degel.marketing.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.common.core.R;
import com.degel.marketing.entity.Banner;
import com.degel.marketing.service.BannerService;
import com.degel.marketing.vo.BannerCreateVo;
import com.degel.marketing.vo.BannerVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.stream.Collectors;

/**
 * 平台轮播图管理（经网关 /marketing/platform/**，admin-urls 限平台角色）。
 *
 * 网关安全：AuthFilter 已将 c_end 令牌限制在 /app/**（2026-09-11 根治，见 known-issues），
 * 本 controller 不再依赖 X-Shop-Id==0 弱兜底作为唯一防线。
 */
@RestController
@RequestMapping("/platform/banner")
@RequiredArgsConstructor
public class PlatformBannerController {

    private final BannerService bannerService;

    @GetMapping("/page")
    public R<IPage<BannerVo>> page(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long pageSize,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "status", required = false) Integer status) {
        IPage<Banner> result = bannerService.page(page, pageSize, title, status);
        Page<BannerVo> vo = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        vo.setRecords(result.getRecords().stream().map(BannerVo::from).collect(Collectors.toList()));
        return R.ok(vo);
    }

    /** 新增/编辑（vo.id 空=新增） */
    @PostMapping
    public R<Void> saveOrUpdate(
            @Valid @RequestBody BannerCreateVo vo,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        if (shopId == null || shopId > 0) {
            return R.fail("仅平台可管理轮播图");
        }
        bannerService.saveOrUpdateBanner(vo);
        return R.ok();
    }

    /** 逻辑删除 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        bannerService.delete(id);
        return R.ok();
    }

    /** 上/下架取反（1↔0） */
    @PutMapping("/toggle-status/{id}")
    public R<Void> toggleStatus(@PathVariable Long id) {
        bannerService.toggleStatus(id);
        return R.ok();
    }
}
