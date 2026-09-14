package com.degel.product.controller;

import com.degel.common.core.R;
import com.degel.product.service.IProductSpuService;
import com.degel.product.vo.InnerRatingVo;
import com.degel.product.vo.SpuImageVo;
import com.degel.product.vo.SpuListVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内部接口（degel-order/degel-app 专用，经 Feign 直连；网关侧 /product/inner/ 已列入 internal-urls 禁止外部访问）
 */
@RestController
@RequestMapping("/inner/spu")
@RequiredArgsConstructor
public class InnerSpuController {

    private final IProductSpuService spuService;

    /**
     * 批量取 SPU 主图（评价列表等展示场景）
     */
    @PostMapping("/batch-images")
    public R<List<SpuImageVo>> batchImages(@RequestBody List<Long> spuIds) {
        return R.ok(spuService.listImagesByIds(spuIds));
    }

    /**
     * 批量查 SPU 列表信息（收藏/足迹等展示场景，返回实时状态与价格；
     * 不存在的 id 静默跳过，@TableLogic 已过滤逻辑删除）
     */
    @PostMapping("/batch")
    public R<List<SpuListVo>> batch(@RequestBody List<Long> spuIds) {
        return R.ok(spuService.listVoByIds(spuIds));
    }

    /**
     * 回写商品评分冗余（评价写入后调用，幂等覆盖；调用方保证聚合值正确）
     */
    @PutMapping("/rating")
    public R<Boolean> updateRating(@RequestBody InnerRatingVo vo) {
        spuService.updateRating(vo.getSpuId(), vo.getRatingAvg(), vo.getRatingCount());
        return R.ok(true);
    }
}
