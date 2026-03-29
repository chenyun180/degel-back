package com.degel.app.controller;

import com.degel.app.service.AuthService;
import com.degel.app.vo.WxLoginVO;
import com.degel.app.vo.dto.LoginReqVO;
import com.degel.app.vo.dto.WxLoginReqVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 认证控制器（无需 JWT，登录接口）
 */
@RestController
@RequestMapping("/app/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 微信小程序登录
     * POST /app/auth/wx-login（无需 JWT）
     */
    @PostMapping("/wx-login")
    public R<WxLoginVO> wxLogin(@Valid @RequestBody WxLoginReqVO req) {
        return R.ok(authService.wxLogin(req));
    }

    /**
     * H5 账号密码登录
     * POST /app/auth/login（无需 JWT）
     */
    @PostMapping("/login")
    public R<WxLoginVO> login(@Valid @RequestBody LoginReqVO req) {
        return R.ok(authService.login(req));
    }
}
