package com.degel.app.controller;

import com.degel.app.context.UserContext;
import com.degel.app.service.CartService;
import com.degel.app.vo.CartCheckVO;
import com.degel.app.vo.CartItemVO;
import com.degel.app.vo.dto.CartAddReqVO;
import com.degel.app.vo.dto.CartCheckReqVO;
import com.degel.app.vo.dto.CartUpdateReqVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 购物车 Controller（需要登录）
 */
@RestController
@RequestMapping("/app/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * B-05：加入购物车
     * POST /app/cart
     */
    @PostMapping
    public R<Void> addToCart(@Valid @RequestBody CartAddReqVO reqVO) {
        Long userId = UserContext.getUserId();
        cartService.addToCart(userId, reqVO);
        return R.ok();
    }

    /**
     * B-06：购物车列表
     * GET /app/cart
     */
    @GetMapping
    public R<List<CartItemVO>> listCart() {
        Long userId = UserContext.getUserId();
        return R.ok(cartService.listCart(userId));
    }

    /**
     * B-07：修改购物车数量
     * PUT /app/cart/{id}
     */
    @PutMapping("/{id}")
    public R<Void> updateQuantity(@PathVariable Long id,
                                  @Valid @RequestBody CartUpdateReqVO reqVO) {
        Long userId = UserContext.getUserId();
        cartService.updateQuantity(userId, id, reqVO);
        return R.ok();
    }

    /**
     * B-08：批量删除购物车
     * DELETE /app/cart?ids=1,2,3
     */
    @DeleteMapping
    public R<Void> deleteCart(@RequestParam String ids) {
        Long userId = UserContext.getUserId();
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
        cartService.deleteCart(userId, idList);
        return R.ok();
    }

    /**
     * B-09：勾选预览结算
     * POST /app/cart/check
     */
    @PostMapping("/check")
    public R<CartCheckVO> checkCart(@Valid @RequestBody CartCheckReqVO reqVO) {
        Long userId = UserContext.getUserId();
        return R.ok(cartService.checkCart(userId, reqVO));
    }
}
