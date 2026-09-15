package com.degel.app.feign.fallback;

import com.degel.app.feign.BannerFeignClient;
import com.degel.app.vo.BannerVO;
import com.degel.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * BannerFeignClient 降级实现。查询类降级返回 fail，BFF 层转空列表给前端
 * （banner 挂了不能影响首页）。
 */
@Slf4j
@Component
public class BannerFeignFallback implements BannerFeignClient {

    @Override
    public R<List<BannerVO>> list() {
        log.error("[BannerFeignFallback] list 降级");
        return R.fail(50001, "banner服务暂不可用");
    }
}
