package com.degel.app.service;

import com.degel.app.vo.CartCheckVO;
import com.degel.app.vo.CartItemVO;
import com.degel.app.vo.dto.CartAddReqVO;
import com.degel.app.vo.dto.CartCheckReqVO;
import com.degel.app.vo.dto.CartUpdateReqVO;

import java.util.List;

/**
 * 购物车 Service
 */
public interface CartService {

    /**
     * 加入购物车（B-05）
     */
    void addToCart(Long userId, CartAddReqVO reqVO);

    /**
     * 获取购物车列表（B-06）
     */
    List<CartItemVO> listCart(Long userId);

    /**
     * 修改购物车数量（B-07）
     */
    void updateQuantity(Long userId, Long cartId, CartUpdateReqVO reqVO);

    /**
     * 批量删除购物车记录（B-08）
     */
    void deleteCart(Long userId, List<Long> ids);

    /**
     * 勾选预览结算（B-09）
     */
    CartCheckVO checkCart(Long userId, CartCheckReqVO reqVO);
}
