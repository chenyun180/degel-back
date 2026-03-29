package com.degel.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.app.entity.MallPaymentLog;
import com.degel.app.exception.BusinessException;
import com.degel.app.feign.OrderFeignClient;
import com.degel.app.mapper.MallPaymentLogMapper;
import com.degel.app.service.AfterSaleService;
import com.degel.app.vo.*;
import com.degel.app.vo.dto.AfterSaleCreateInnerReqVO;
import com.degel.app.vo.dto.AfterSaleCreateReqVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 售后/退款服务实现（C-10 / C-11 / C-12）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSaleServiceImpl implements AfterSaleService {

    private final OrderFeignClient orderFeignClient;
    private final MallPaymentLogMapper mallPaymentLogMapper;

    // =========================================================
    // C-10: POST /app/aftersale — 申请售后/退款
    // =========================================================

    @Override
    public Long applyAfterSale(AfterSaleCreateReqVO reqVO, Long userId) {
        // 1. 查 order_info 校验归属
        R<OrderInfoVO> orderResp = orderFeignClient.getOrder(reqVO.getOrderId());
        if (orderResp == null || orderResp.getCode() != 200 || orderResp.getData() == null) {
            throw BusinessException.of(40015, "订单不存在");
        }
        OrderInfoVO orderInfo = orderResp.getData();
        if (!userId.equals(orderInfo.getUserId())) {
            throw BusinessException.of(40016, "无权操作该订单");
        }

        // 2. 校验订单状态：必须是 status=3（已完成）才能申请售后
        if (!Integer.valueOf(3).equals(orderInfo.getStatus())) {
            throw BusinessException.of(40020, "仅已完成订单可申请售后");
        }

        // 3. 查重：已有 status IN(0,1) 的售后单则拒绝重复申请
        // 使用精确查重接口，避免分页拉 100 条内存过滤超量漏判
        R<Boolean> checkResp = orderFeignClient.existsActiveAfterSale(reqVO.getOrderId(), userId);
        if (checkResp == null || checkResp.getCode() != 200) {
            throw BusinessException.of(50001, "查重校验失败，请稍后重试");
        }
        if (Boolean.TRUE.equals(checkResp.getData())) {
            throw BusinessException.of(40021, "已有进行中的售后申请，请勿重复提交");
        }

        // 4. INSERT order_after_sale（status=0）
        // 退款金额 = 订单实付金额
        AfterSaleCreateInnerReqVO innerReq = new AfterSaleCreateInnerReqVO();
        innerReq.setOrderId(reqVO.getOrderId());
        innerReq.setUserId(userId);
        innerReq.setShopId(orderInfo.getShopId());
        innerReq.setType(1); // 仅退款
        innerReq.setReason(reqVO.getReason());
        innerReq.setRefundAmount(orderInfo.getPayAmount());

        R<Long> createResp = orderFeignClient.createAfterSale(innerReq);
        if (createResp == null || createResp.getCode() != 200 || createResp.getData() == null) {
            throw BusinessException.of(50001, "申请售后失败，请稍后重试");
        }
        return createResp.getData();
    }

    // =========================================================
    // C-11: GET /app/aftersale — 退款列表（分页）
    // =========================================================

    @Override
    public IPage<AfterSaleListVO> listAfterSale(Long userId, Integer page, Integer size) {
        R<IPage<AfterSaleInfoVO>> resp = orderFeignClient.pageAfterSales(userId, null, page, size);
        if (resp == null || resp.getCode() != 200 || resp.getData() == null) {
            throw BusinessException.of(50001, "查询售后列表失败，请稍后重试");
        }
        IPage<AfterSaleInfoVO> rawPage = resp.getData();

        IPage<AfterSaleListVO> resultPage = new Page<>(rawPage.getCurrent(), rawPage.getSize(), rawPage.getTotal());
        List<AfterSaleListVO> records = rawPage.getRecords().stream()
                .map(this::convertToListVO)
                .collect(Collectors.toList());
        resultPage.setRecords(records);
        return resultPage;
    }

    private AfterSaleListVO convertToListVO(AfterSaleInfoVO info) {
        AfterSaleListVO vo = new AfterSaleListVO();
        vo.setId(info.getId());
        vo.setOrderId(info.getOrderId());
        vo.setOrderNo(info.getOrderNo());
        vo.setType(info.getType());
        vo.setStatus(info.getStatus());
        vo.setStatusDesc(getAfterSaleStatusDesc(info.getStatus()));
        vo.setReason(info.getReason());
        vo.setRefundAmount(info.getRefundAmount());
        vo.setCreateTime(info.getCreateTime());
        return vo;
    }

    // =========================================================
    // C-12: GET /app/aftersale/{id} — 退款详情
    // =========================================================

    @Override
    public AfterSaleDetailVO getAfterSaleDetail(Long id, Long userId) {
        // 精确查询售后单，避免全量拉取 1000 条内存过滤
        R<AfterSaleInfoVO> resp = orderFeignClient.getAfterSaleById(id);
        if (resp == null || resp.getCode() != 200 || resp.getData() == null) {
            throw BusinessException.of(40400, "售后单不存在");
        }
        AfterSaleInfoVO targetInfo = resp.getData();

        // 校验归属
        if (!userId.equals(targetInfo.getUserId())) {
            throw BusinessException.of(40016, "无权查看该售后单");
        }

        AfterSaleDetailVO detail = new AfterSaleDetailVO();
        detail.setId(targetInfo.getId());
        detail.setOrderId(targetInfo.getOrderId());
        detail.setOrderNo(targetInfo.getOrderNo());
        detail.setType(targetInfo.getType());
        detail.setStatus(targetInfo.getStatus());
        detail.setStatusDesc(getAfterSaleStatusDesc(targetInfo.getStatus()));
        detail.setReason(targetInfo.getReason());
        detail.setRefundAmount(targetInfo.getRefundAmount());
        detail.setMerchantRemark(targetInfo.getMerchantRemark());
        detail.setCreateTime(targetInfo.getCreateTime());
        detail.setUpdateTime(targetInfo.getUpdateTime());

        // 若 status=1（已同意），查退款流水
        if (Integer.valueOf(1).equals(targetInfo.getStatus())) {
            MallPaymentLog refundLog = mallPaymentLogMapper.selectOne(
                    new LambdaQueryWrapper<MallPaymentLog>()
                            .eq(MallPaymentLog::getOrderId, targetInfo.getOrderId())
                            .eq(MallPaymentLog::getUserId, userId)
                            .eq(MallPaymentLog::getDirection, "refund")
                            .eq(MallPaymentLog::getStatus, 0)
                            .orderByDesc(MallPaymentLog::getCreateTime)
                            .last("LIMIT 1")
            );
            if (refundLog != null) {
                PayLogVO payLog = new PayLogVO();
                payLog.setId(refundLog.getId());
                payLog.setOrderId(refundLog.getOrderId());
                payLog.setOrderNo(refundLog.getOrderNo());
                payLog.setAmount(refundLog.getAmount());
                payLog.setDirection(refundLog.getDirection());
                payLog.setStatus(refundLog.getStatus());
                payLog.setRemark(refundLog.getRemark());
                payLog.setCreateTime(refundLog.getCreateTime());
                detail.setPayLog(payLog);
            }
        }

        return detail;
    }

    // =========================================================
    // 工具方法
    // =========================================================

    private String getAfterSaleStatusDesc(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "待审核";
            case 1: return "已同意";
            case 2: return "已拒绝";
            default: return "未知";
        }
    }
}
