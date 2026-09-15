package com.degel.app.feign;

import com.degel.app.config.FeignConfig;
import com.degel.app.feign.fallback.BannerFeignFallback;
import com.degel.app.vo.BannerVO;
import com.degel.common.core.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 营销轮播图 Feign 客户端
 * 直连 lb://degel-marketing，不带网关 /marketing 前缀（Feign 直连端点即控制器原始路径）。
 * /inner/ 前缀由 marketing 侧 InnerTokenFilter 保护，FeignConfig 已统一注入 X-Inner-Token。
 */
@FeignClient(name = "degel-marketing", path = "/inner/banner", contextId = "appBannerFeignClient",
        configuration = FeignConfig.class, fallback = BannerFeignFallback.class)
public interface BannerFeignClient {

    /**
     * C 端生效 banner（最多 10 条，按 sort）
     */
    @GetMapping("/list")
    R<List<BannerVO>> list();
}
