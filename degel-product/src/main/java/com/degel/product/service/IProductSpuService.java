package com.degel.product.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.product.entity.ProductSpu;
import com.degel.product.vo.*;

public interface IProductSpuService extends IService<ProductSpu> {

    IPage<ProductSpu> pageSpu(Page<ProductSpu> page, ProductSpu query);

    void createSpu(SpuCreateVo vo, Long shopId);

    void updateSpu(SpuUpdateVo vo, Long shopId);

    SpuDetailVo getSpuDetail(Long id, Long shopId);

    void deleteSpu(Long id, Long shopId);

    void submitAudit(Long id);

    void audit(AuditVo auditVo, Long auditorId);

    void toggleStatus(Long id, Long shopId);

    void updateStock(StockUpdateVo vo, Long shopId);
}
