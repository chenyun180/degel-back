package com.degel.app.controller;

import com.degel.app.service.BannerService;
import com.degel.app.vo.BannerVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 轮播图 Controller（无需登录）
 */
@RestController
@RequestMapping("/app/banner")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    /**
     * GET /app/banner/list - 生效中的营销轮播
     */
    @GetMapping("/list")
    public R<List<BannerVO>> list() {
        return R.ok(bannerService.list());
    }
}
