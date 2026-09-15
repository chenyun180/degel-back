package com.degel.marketing.controller;

import com.degel.common.core.R;
import com.degel.marketing.service.BannerService;
import com.degel.marketing.vo.BannerVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * C 端生效 banner（degel-app 经 Feign 调用；InnerTokenFilter 已保护 /inner/ 前缀，
 * 网关 internal-urls 拒绝外部经网关访问，Feign 经 Nacos 直连）。
 */
@RestController
@RequestMapping("/inner/banner")
@RequiredArgsConstructor
public class InnerBannerController {

    private final BannerService bannerService;

    /** C 端生效列表：上架且在有效期内，sort 升序，最多 10 条（不含 delFlag） */
    @GetMapping("/list")
    public R<List<BannerVo>> list() {
        return R.ok(bannerService.listActive().stream()
                .map(BannerVo::from)
                .collect(Collectors.toList()));
    }
}
