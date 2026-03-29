package com.degel.app.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.vo.AfterSaleDetailVO;
import com.degel.app.vo.AfterSaleListVO;
import com.degel.app.vo.dto.AfterSaleCreateReqVO;

/**
 * 售后/退款服务接口
 */
public interface AfterSaleService {

    /**
     * C-10: 申请售后/退款
     *
     * @param reqVO  申请请求
     * @param userId 当前用户ID
     * @return 售后单ID
     */
    Long applyAfterSale(AfterSaleCreateReqVO reqVO, Long userId);

    /**
     * C-11: 我的退款列表（分页）
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页大小
     * @return 分页结果
     */
    IPage<AfterSaleListVO> listAfterSale(Long userId, Integer page, Integer size);

    /**
     * C-12: 退款详情
     *
     * @param id     售后单ID
     * @param userId 当前用户ID（校验归属）
     * @return 详情
     */
    AfterSaleDetailVO getAfterSaleDetail(Long id, Long userId);
}
