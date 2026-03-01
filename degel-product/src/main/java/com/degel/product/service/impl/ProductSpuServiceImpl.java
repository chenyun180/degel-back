package com.degel.product.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.degel.common.core.Constants;
import com.degel.common.core.exception.BusinessException;
import com.degel.product.entity.ProductSku;
import com.degel.product.entity.ProductSpu;
import com.degel.product.mapper.ProductSpuMapper;
import com.degel.product.service.IProductSkuService;
import com.degel.product.service.IProductSpuService;
import com.degel.product.vo.*;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductSpuServiceImpl extends ServiceImpl<ProductSpuMapper, ProductSpu>
        implements IProductSpuService {

    private final IProductSkuService skuService;

    @Override
    public IPage<ProductSpu> pageSpu(Page<ProductSpu> page, ProductSpu query) {
        LambdaQueryWrapper<ProductSpu> wrapper = new LambdaQueryWrapper<>();
        if (query.getShopId() != null) {
            wrapper.eq(ProductSpu::getShopId, query.getShopId());
        }
        if (query.getCategoryId() != null) {
            wrapper.eq(ProductSpu::getCategoryId, query.getCategoryId());
        }
        if (query.getAuditStatus() != null) {
            wrapper.eq(ProductSpu::getAuditStatus, query.getAuditStatus());
        }
        if (StrUtil.isNotBlank(query.getName())) {
            wrapper.like(ProductSpu::getName, query.getName());
        }
        wrapper.orderByDesc(ProductSpu::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createSpu(SpuCreateVo vo, Long shopId) {
        ProductSpu spu = new ProductSpu();
        spu.setShopId(shopId);
        spu.setCategoryId(vo.getCategoryId());
        spu.setName(vo.getName());
        spu.setSubtitle(vo.getSubtitle());
        spu.setDescription(vo.getDescription());
        spu.setDetailContent(vo.getDetailContent());
        spu.setMainImage(vo.getMainImage());
        spu.setImages(vo.getImages());
        spu.setKeyword(vo.getKeyword());
        spu.setAuditStatus(Constants.AUDIT_DRAFT);
        spu.setStatus(0);
        save(spu);

        List<ProductSku> skuList = vo.getSkuList().stream().map(item -> {
            ProductSku sku = new ProductSku();
            sku.setSpuId(spu.getId());
            sku.setSkuCode(item.getSkuCode());
            sku.setSpecData(item.getSpecData());
            sku.setPrice(item.getPrice());
            sku.setOriginalPrice(item.getOriginalPrice());
            sku.setCostPrice(item.getCostPrice());
            sku.setStock(item.getStock());
            sku.setStockWarning(item.getStockWarning());
            sku.setWeight(item.getWeight());
            sku.setImage(item.getImage());
            return sku;
        }).collect(Collectors.toList());
        skuService.saveBatch(skuList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSpu(SpuUpdateVo vo, Long shopId) {
        ProductSpu existing = getById(vo.getId());
        if (existing == null) {
            throw new BusinessException("商品不存在");
        }
        if (!existing.getShopId().equals(shopId)) {
            throw new BusinessException("无权操作此商品");
        }
        if (existing.getAuditStatus() == Constants.AUDIT_PENDING) {
            throw new BusinessException("待审核状态的商品不允许编辑");
        }

        ProductSpu spu = new ProductSpu();
        spu.setId(vo.getId());
        spu.setCategoryId(vo.getCategoryId());
        spu.setName(vo.getName());
        spu.setSubtitle(vo.getSubtitle());
        spu.setDescription(vo.getDescription());
        spu.setDetailContent(vo.getDetailContent());
        spu.setMainImage(vo.getMainImage());
        spu.setImages(vo.getImages());
        spu.setKeyword(vo.getKeyword());
        spu.setAuditStatus(Constants.AUDIT_DRAFT);
        updateById(spu);

        skuService.deleteBySpuId(vo.getId());

        List<ProductSku> skuList = vo.getSkuList().stream().map(item -> {
            ProductSku sku = new ProductSku();
            sku.setSpuId(vo.getId());
            sku.setSkuCode(item.getSkuCode());
            sku.setSpecData(item.getSpecData());
            sku.setPrice(item.getPrice());
            sku.setOriginalPrice(item.getOriginalPrice());
            sku.setCostPrice(item.getCostPrice());
            sku.setStock(item.getStock());
            sku.setStockWarning(item.getStockWarning());
            sku.setWeight(item.getWeight());
            sku.setImage(item.getImage());
            return sku;
        }).collect(Collectors.toList());
        skuService.saveBatch(skuList);
    }

    @Override
    public SpuDetailVo getSpuDetail(Long id, Long shopId) {
        ProductSpu spu = getById(id);
        if (spu == null) {
            throw new BusinessException("商品不存在");
        }
        if (shopId != null && !spu.getShopId().equals(shopId)) {
            throw new BusinessException("无权查看此商品");
        }
        SpuDetailVo detail = new SpuDetailVo();
        detail.setSpu(spu);
        detail.setSkuList(skuService.listBySpuId(id));
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSpu(Long id, Long shopId) {
        ProductSpu spu = getById(id);
        if (spu == null) {
            throw new BusinessException("商品不存在");
        }
        if (!spu.getShopId().equals(shopId)) {
            throw new BusinessException("无权删除此商品");
        }
        removeById(id);
        skuService.deleteBySpuId(id);
    }

    @Override
    public void submitAudit(Long id) {
        ProductSpu spu = getById(id);
        if (spu == null) {
            throw new BusinessException("商品不存在");
        }
        int status = spu.getAuditStatus();
        if (status != Constants.AUDIT_DRAFT && status != Constants.AUDIT_REJECTED) {
            throw new BusinessException("只有草稿或已驳回的商品才能提交审核");
        }
        ProductSpu update = new ProductSpu();
        update.setId(id);
        update.setAuditStatus(Constants.AUDIT_PENDING);
        update.setRejectReason("");
        updateById(update);
    }

    @Override
    public void audit(AuditVo auditVo, Long auditorId) {
        ProductSpu spu = getById(auditVo.getSpuId());
        if (spu == null) {
            throw new BusinessException("商品不存在");
        }
        if (spu.getAuditStatus() != Constants.AUDIT_PENDING) {
            throw new BusinessException("只有待审核的商品才能执行审核操作");
        }

        ProductSpu update = new ProductSpu();
        update.setId(auditVo.getSpuId());
        update.setAuditorId(auditorId);
        update.setAuditTime(LocalDateTime.now());

        if (Boolean.TRUE.equals(auditVo.getPassed())) {
            update.setAuditStatus(Constants.AUDIT_APPROVED);
            update.setRejectReason("");
        } else {
            update.setAuditStatus(Constants.AUDIT_REJECTED);
            update.setRejectReason(auditVo.getRejectReason());
        }
        updateById(update);
    }

    @Override
    public void toggleStatus(Long id, Long shopId) {
        ProductSpu spu = getById(id);
        if (spu == null) {
            throw new BusinessException("商品不存在");
        }
        if (!spu.getShopId().equals(shopId)) {
            throw new BusinessException("无权操作此商品");
        }

        if (spu.getStatus() == 1) {
            ProductSpu update = new ProductSpu();
            update.setId(id);
            update.setStatus(0);
            updateById(update);
            return;
        }

        if (spu.getAuditStatus() != Constants.AUDIT_APPROVED) {
            throw new BusinessException("商品需审核通过后才能上架");
        }
        List<ProductSku> skuList = skuService.listBySpuId(id);
        int totalStock = skuList.stream().mapToInt(ProductSku::getStock).sum();
        if (totalStock <= 0) {
            throw new BusinessException("商品库存为0，无法上架");
        }

        ProductSpu update = new ProductSpu();
        update.setId(id);
        update.setStatus(1);
        updateById(update);
    }

    @Override
    public void updateStock(StockUpdateVo vo, Long shopId) {
        ProductSku sku = skuService.getById(vo.getSkuId());
        if (sku == null) {
            throw new BusinessException("SKU不存在");
        }
        ProductSpu spu = getById(sku.getSpuId());
        if (spu == null || !spu.getShopId().equals(shopId)) {
            throw new BusinessException("无权操作此SKU");
        }
        ProductSku update = new ProductSku();
        update.setId(vo.getSkuId());
        update.setStock(vo.getStock());
        skuService.updateById(update);
    }
}
