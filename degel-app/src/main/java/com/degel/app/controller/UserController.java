package com.degel.app.controller;

import com.degel.app.context.UserContext;
import com.degel.app.service.AddressService;
import com.degel.app.vo.AddressVO;
import com.degel.app.vo.dto.AddressCreateReqVO;
import com.degel.app.vo.dto.AddressUpdateReqVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 用户中心控制器
 * - 收货地址 5 个接口（需要 JWT，由网关 AppJwtFilter 鉴权，通过 UserContext 获取 userId）
 */
@RestController
@RequestMapping("/app/user")
@RequiredArgsConstructor
public class UserController {

    private final AddressService addressService;

    /**
     * A-05: 新增收货地址
     * POST /app/user/address
     */
    @PostMapping("/address")
    public R<AddressVO> createAddress(@Valid @RequestBody AddressCreateReqVO req) {
        Long userId = UserContext.getUserId();
        return R.ok(addressService.create(userId, req));
    }

    /**
     * A-06: 编辑收货地址
     * PUT /app/user/address/{id}
     */
    @PutMapping("/address/{id}")
    public R<AddressVO> updateAddress(@PathVariable Long id,
                                       @Valid @RequestBody AddressUpdateReqVO req) {
        Long userId = UserContext.getUserId();
        return R.ok(addressService.update(userId, id, req));
    }

    /**
     * A-07: 删除收货地址
     * DELETE /app/user/address/{id}
     */
    @DeleteMapping("/address/{id}")
    public R<Void> deleteAddress(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        addressService.delete(userId, id);
        return R.ok();
    }

    /**
     * A-08: 获取收货地址列表
     * GET /app/user/address
     */
    @GetMapping("/address")
    public R<List<AddressVO>> listAddress() {
        Long userId = UserContext.getUserId();
        return R.ok(addressService.list(userId));
    }

    /**
     * A-09: 设为默认地址
     * PUT /app/user/address/{id}/default
     */
    @PutMapping("/address/{id}/default")
    public R<Void> setDefaultAddress(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        addressService.setDefault(userId, id);
        return R.ok();
    }
}
