package com.degel.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.degel.product.entity.ProductSpu;
import com.degel.product.vo.HotSaleVo;
import com.degel.product.vo.VisitorRankVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final IProductSpuService spuService;

    public List<HotSaleVo> getHotSaleRank(Long shopId, String period) {
        List<ProductSpu> spuList = spuService.list(new LambdaQueryWrapper<ProductSpu>()
                .eq(ProductSpu::getShopId, shopId)
                .eq(ProductSpu::getDelFlag, 0)
                .orderByDesc(ProductSpu::getSaleCount)
                .last("LIMIT 10"));

        return spuList.stream().map(spu -> {
            HotSaleVo vo = new HotSaleVo();
            vo.setSpuId(spu.getId());
            vo.setSpuName(spu.getName());
            vo.setMainImage(spu.getMainImage());
            vo.setSaleCount(spu.getSaleCount() != null ? spu.getSaleCount() : 0);
            vo.setSaleAmount(BigDecimal.ZERO);
            vo.setGrowthRate(BigDecimal.ZERO);
            return vo;
        }).collect(Collectors.toList());
    }

    public List<VisitorRankVo> getVisitorRank(Long shopId, String period) {
        List<ProductSpu> spuList = spuService.list(new LambdaQueryWrapper<ProductSpu>()
                .eq(ProductSpu::getShopId, shopId)
                .eq(ProductSpu::getDelFlag, 0)
                .gt(ProductSpu::getViewCount, 0)
                .orderByDesc(ProductSpu::getViewCount)
                .last("LIMIT 10"));

        return spuList.stream().map(spu -> {
            VisitorRankVo vo = new VisitorRankVo();
            vo.setSpuId(spu.getId());
            vo.setSpuName(spu.getName());
            vo.setMainImage(spu.getMainImage());
            vo.setViewCount(spu.getViewCount() != null ? spu.getViewCount() : 0);
            vo.setOrderCount(spu.getSaleCount() != null ? spu.getSaleCount() : 0);
            if (vo.getViewCount() > 0) {
                vo.setConversionRate(BigDecimal.valueOf(vo.getOrderCount())
                        .divide(BigDecimal.valueOf(vo.getViewCount()), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)));
            } else {
                vo.setConversionRate(BigDecimal.ZERO);
            }
            return vo;
        }).collect(Collectors.toList());
    }
}
