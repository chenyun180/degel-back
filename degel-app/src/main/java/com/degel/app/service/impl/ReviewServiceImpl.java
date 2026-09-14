package com.degel.app.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.app.feign.ProductFeignClient;
import com.degel.app.feign.ReviewFeignClient;
import com.degel.app.vo.ProductSkuVO;
import com.degel.app.vo.ReviewCreateInnerReqVO;
import com.degel.app.vo.ReviewCreateReqVO;
import com.degel.app.vo.ReviewVO;
import com.degel.common.core.R;
import com.degel.app.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评价编排（playbook 约定：编排逻辑放 service，不放 controller）。
 * userId 一律取登录态（UserContext），服务端 degel-order 再校验订单归属，双保险。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements com.degel.app.service.ReviewService {

    private final ReviewFeignClient reviewFeignClient;
    private final ProductFeignClient productFeignClient;

    @org.springframework.beans.factory.annotation.Value("${degel.app.file-base-url}")
    private String fileBaseUrl;

    @Override
    public void createReview(Long userId, ReviewCreateReqVO reqVO) {
        ReviewCreateInnerReqVO inner = new ReviewCreateInnerReqVO();
        inner.setOrderId(reqVO.getOrderId());
        inner.setOrderItemId(reqVO.getOrderItemId());
        inner.setUserId(userId);
        inner.setStar(reqVO.getStar());
        inner.setContent(reqVO.getContent());
        R<Long> resp = reviewFeignClient.create(inner);
        if (resp == null || resp.getCode() != 200) {
            throw BusinessException.of(resp != null ? resp.getCode() : 50001,
                    resp != null ? resp.getMsg() : "提交评价失败，请稍后重试");
        }
    }

    @Override
    public IPage<ReviewVO> pageBySpu(Long spuId, int page, int pageSize) {
        R<Page<ReviewVO>> resp = reviewFeignClient.pageBySpu(spuId, page, pageSize);
        if (resp == null || resp.getCode() != 200 || resp.getData() == null) {
            // 降级返回空页：商品主信息不受影响（详情页评价区显示空态而非报错）
            log.error("[ReviewService] 商品评价查询降级 spuId={}", spuId);
            return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize);
        }
        return resp.getData();
    }

    @Override
    public IPage<ReviewVO> pageMine(Long userId, int page, int pageSize) {
        R<Page<ReviewVO>> resp = reviewFeignClient.pageMine(userId, page, pageSize);
        if (resp == null || resp.getCode() != 200 || resp.getData() == null) {
            throw BusinessException.of(resp != null ? resp.getCode() : 50001,
                    resp != null ? resp.getMsg() : "查询评价失败，请稍后重试");
        }
        fillSkuImage(resp.getData());
        return resp.getData();
    }

    /**
     * 按页内 skuId 批量回查 SKU 图片填充 skuImage；SKU 图为空（存量数据普遍如此）
     * 再按 spuId 批量回退到 SPU 主图。
     * 图片是展示性增强：批量查询失败只降级为无图，不让"我的评价"整体报错。
     */
    private void fillSkuImage(IPage<ReviewVO> page) {
        List<ReviewVO> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return;
        }
        try {
            List<Long> skuIds = records.stream()
                    .map(ReviewVO::getSkuId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (!skuIds.isEmpty()) {
                R<List<ProductSkuVO>> resp = productFeignClient.batchGetSku(skuIds);
                if (resp != null && resp.getCode() == 200 && resp.getData() != null) {
                    Map<Long, String> imageBySkuId = resp.getData().stream()
                            .filter(sku -> sku.getId() != null && sku.getImage() != null && !sku.getImage().isEmpty())
                            .collect(Collectors.toMap(ProductSkuVO::getId, ProductSkuVO::getImage, (a, b) -> a));
                    records.forEach(r -> r.setSkuImage(fileUrl(imageBySkuId.get(r.getSkuId()))));
                } else {
                    log.warn("[ReviewService] SKU 批量查询失败，评价图回退 SPU 主图: {}",
                            resp == null ? "null" : resp.getMsg());
                }
            }
            // SKU 无图的评价，回退到 SPU 主图
            List<Long> spuIds = records.stream()
                    .filter(r -> r.getSkuImage() == null || r.getSkuImage().isEmpty())
                    .map(ReviewVO::getSpuId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (spuIds.isEmpty()) {
                return;
            }
            R<List<com.degel.app.vo.ProductSpuVO>> spuResp = productFeignClient.batchGetSpuImages(spuIds);
            if (spuResp == null || spuResp.getCode() != 200 || spuResp.getData() == null) {
                log.warn("[ReviewService] SPU 主图批量查询失败，评价降级为无图: {}",
                        spuResp == null ? "null" : spuResp.getMsg());
                return;
            }
            Map<Long, String> imageBySpuId = spuResp.getData().stream()
                    .filter(spu -> spu.getId() != null && spu.getMainImage() != null)
                    .collect(Collectors.toMap(com.degel.app.vo.ProductSpuVO::getId, com.degel.app.vo.ProductSpuVO::getMainImage, (a, b) -> a));
            records.stream()
                    .filter(r -> r.getSkuImage() == null || r.getSkuImage().isEmpty())
                    .forEach(r -> r.setSkuImage(fileUrl(imageBySpuId.get(r.getSpuId()))));
        } catch (Exception e) {
            log.warn("[ReviewService] 评价图片批量查询异常，我的评价降级为无图", e);
        }
    }

    /** 图片相对路径拼完整 URL（存储为裸 key，展示时补 file-base-url 前缀，同 CartServiceImpl） */
    private String fileUrl(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }
        return fileBaseUrl + "/file/view/" + key;
    }

    @Override
    public List<Long> reviewedItemIds(Long orderId, Long userId) {
        R<List<Long>> resp = reviewFeignClient.reviewedItemIds(orderId, userId);
        return resp != null && resp.getCode() == 200 && resp.getData() != null
                ? resp.getData() : java.util.Collections.emptyList();
    }

    @Override
    public IPage<com.degel.app.vo.PendingOrderVO> pagePendingOrders(Long userId, int page, int pageSize) {
        R<Page<com.degel.app.vo.PendingOrderVO>> resp = reviewFeignClient.pendingOrders(userId, page, pageSize);
        if (resp == null || resp.getCode() != 200 || resp.getData() == null) {
            log.error("[ReviewService] 待评价订单查询降级 userId={}", userId);
            return new Page<>(page, pageSize);
        }
        return resp.getData();
    }
}
