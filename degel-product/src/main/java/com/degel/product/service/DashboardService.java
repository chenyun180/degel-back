package com.degel.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.common.core.Constants;
import com.degel.product.entity.ProductSku;
import com.degel.product.entity.ProductSpu;
import com.degel.product.vo.DashboardOverviewVo;
import com.degel.product.vo.PendingCountsVo;
import com.degel.product.vo.StockWarningVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final IProductSpuService spuService;
    private final IProductSkuService skuService;

    public DashboardOverviewVo getTodayOverview(Long shopId) {
        // ⚠️ 空壳实现：GMV/订单数属 degel_order 库，product 服务无法计算，恒返回 0。
        // 真实的流水/订单数据在 degel-order 的 /platform/dashboard/overview（平台维度）。
        DashboardOverviewVo vo = new DashboardOverviewVo();
        vo.setTodayGmv(BigDecimal.ZERO);
        vo.setTodayOrderCount(0);
        vo.setTodayVisitorCount(0);
        vo.setYesterdayGmv(BigDecimal.ZERO);
        vo.setYesterdayOrderCount(0);
        return vo;
    }

    public IPage<StockWarningVo> getStockWarnings(Page<?> page, Long shopId) {
        List<Long> spuIds = spuService.list(new LambdaQueryWrapper<ProductSpu>()
                .eq(ProductSpu::getShopId, shopId)
                .eq(ProductSpu::getDelFlag, 0)
                .select(ProductSpu::getId))
                .stream().map(ProductSpu::getId).collect(Collectors.toList());

        if (spuIds.isEmpty()) {
            return new Page<>(page.getCurrent(), page.getSize(), 0);
        }

        List<ProductSku> warningSkus = skuService.list(new LambdaQueryWrapper<ProductSku>()
                .in(ProductSku::getSpuId, spuIds)
                .eq(ProductSku::getDelFlag, 0)
                .gt(ProductSku::getStockWarning, 0)
                .apply("stock <= stock_warning"));

        // 无预警 SKU 时直接返回空页——listByIds(空集合) 会生成 IN () 非法 SQL
        if (warningSkus.isEmpty()) {
            Page<StockWarningVo> empty = new Page<>(page.getCurrent(), page.getSize(), 0);
            empty.setRecords(Collections.emptyList());
            return empty;
        }

        Map<Long, String> spuNameMap = spuService.listByIds(
                warningSkus.stream().map(ProductSku::getSpuId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProductSpu::getId, ProductSpu::getName));

        List<StockWarningVo> voList = warningSkus.stream().map(sku -> {
            StockWarningVo vo = new StockWarningVo();
            vo.setSpuName(spuNameMap.getOrDefault(sku.getSpuId(), ""));
            vo.setSkuCode(sku.getSkuCode());
            vo.setSpecData(sku.getSpecData());
            vo.setStock(sku.getStock());
            vo.setStockWarning(sku.getStockWarning());
            return vo;
        }).collect(Collectors.toList());

        int total = voList.size();
        int fromIndex = (int) ((page.getCurrent() - 1) * page.getSize());
        int toIndex = Math.min(fromIndex + (int) page.getSize(), total);
        List<StockWarningVo> pageData = fromIndex < total
                ? voList.subList(fromIndex, toIndex)
                : Collections.emptyList();

        Page<StockWarningVo> result = new Page<>(page.getCurrent(), page.getSize(), total);
        result.setRecords(pageData);
        return result;
    }

    public PendingCountsVo getPendingCounts(Long shopId) {
        PendingCountsVo vo = new PendingCountsVo();
        vo.setPendingShipment(0);
        vo.setPendingAfterSale(0);
        // shopId=0/null 为平台用户（网关注入 X-Shop-Id=0），不加店铺过滤即全平台维度
        boolean filterShop = shopId != null && shopId > 0;

        List<Long> spuIds = spuService.list(new LambdaQueryWrapper<ProductSpu>()
                .eq(filterShop, ProductSpu::getShopId, shopId)
                .eq(ProductSpu::getDelFlag, 0)
                .select(ProductSpu::getId))
                .stream().map(ProductSpu::getId).collect(Collectors.toList());

        if (spuIds.isEmpty()) {
            vo.setStockWarningCount(0);
        } else {
            long count = skuService.count(new LambdaQueryWrapper<ProductSku>()
                    .in(ProductSku::getSpuId, spuIds)
                    .eq(ProductSku::getDelFlag, 0)
                    .gt(ProductSku::getStockWarning, 0)
                    .apply("stock <= stock_warning"));
            vo.setStockWarningCount((int) count);
        }

        long auditCount = spuService.count(new LambdaQueryWrapper<ProductSpu>()
                .eq(filterShop, ProductSpu::getShopId, shopId)
                .eq(ProductSpu::getAuditStatus, Constants.AUDIT_PENDING)
                .eq(ProductSpu::getDelFlag, 0));
        vo.setPendingAudit((int) auditCount);

        return vo;
    }
}
