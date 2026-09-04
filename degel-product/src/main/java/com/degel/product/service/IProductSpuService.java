package com.degel.product.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.product.entity.ProductSpu;
import com.degel.product.vo.*;

import java.util.List;

public interface IProductSpuService extends IService<ProductSpu> {

    IPage<SpuListVo> pageSpu(Page<ProductSpu> page, ProductSpu query);

    IPage<SpuListVo> pageSpu(Page<ProductSpu> page, ProductSpu query, String sort);

    void createSpu(SpuCreateVo vo, Long shopId);

    void updateSpu(SpuUpdateVo vo, Long shopId);

    SpuDetailVo getSpuDetail(Long id, Long shopId);

    void deleteSpu(Long id, Long shopId);

    void submitAudit(Long id, Long shopId);

    /** 批量提交审核，返回成功提交的数量；任一商品不合法则整体回滚 */
    int submitAuditBatch(List<Long> ids, Long shopId);

    void audit(AuditVo auditVo, Long auditorId);

    void toggleStatus(Long id, Long shopId);

    void updateStock(StockUpdateVo vo, Long shopId);
}
