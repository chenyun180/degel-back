package com.degel.app.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.context.UserContext;
import com.degel.app.service.AfterSaleService;
import com.degel.app.vo.AfterSaleDetailVO;
import com.degel.app.vo.AfterSaleListVO;
import com.degel.app.vo.dto.AfterSaleCreateReqVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 售后/退款控制器
 * C-10: POST /app/aftersale          申请退款
 * C-11: GET  /app/aftersale          退款列表
 * C-12: GET  /app/aftersale/{id}     退款详情
 */
@RestController
@RequestMapping("/app/aftersale")
@RequiredArgsConstructor
public class AfterSaleController {

    private final AfterSaleService afterSaleService;

    /**
     * C-10: 申请售后/退款
     */
    @PostMapping
    public R<Long> applyAfterSale(@RequestBody @Validated AfterSaleCreateReqVO reqVO) {
        Long userId = UserContext.getUserId();
        Long afterSaleId = afterSaleService.applyAfterSale(reqVO, userId);
        return R.ok(afterSaleId);
    }

    /**
     * C-11: 我的退款列表（分页）
     */
    @GetMapping
    public R<IPage<AfterSaleListVO>> listAfterSale(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size
    ) {
        Long userId = UserContext.getUserId();
        IPage<AfterSaleListVO> result = afterSaleService.listAfterSale(userId, page, size);
        return R.ok(result);
    }

    /**
     * C-12: 退款详情
     */
    @GetMapping("/{id}")
    public R<AfterSaleDetailVO> getAfterSaleDetail(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        AfterSaleDetailVO result = afterSaleService.getAfterSaleDetail(id, userId);
        return R.ok(result);
    }
}
