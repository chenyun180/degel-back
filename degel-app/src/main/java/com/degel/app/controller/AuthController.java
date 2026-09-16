package com.degel.app.controller;

import com.degel.app.exception.BusinessException;
import com.degel.app.service.AuthService;
import com.degel.app.util.RedisRateLimiter;
import com.degel.app.vo.WxLoginVO;
import com.degel.app.vo.dto.LoginReqVO;
import com.degel.app.vo.dto.WxLoginReqVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * 认证控制器（无需 JWT，登录接口）
 */
@RestController
@RequestMapping("/app/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 登录防刷：同手机号 5 次/分钟、同 IP 20 次/分钟（BCrypt 校验约 50-100ms/次，是打满 CPU 的最廉价入口） */
    private static final int LOGIN_PHONE_LIMIT = 5;
    private static final int LOGIN_IP_LIMIT = 20;
    private static final int LOGIN_WINDOW_SECONDS = 60;

    private final AuthService authService;
    private final RedisRateLimiter rateLimiter;

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
     * POST /app/auth/login（无需 JWT）。错误码：40027 尝试过于频繁
     */
    @PostMapping("/login")
    public R<WxLoginVO> login(@Valid @RequestBody LoginReqVO req, HttpServletRequest request) {
        String ip = RedisRateLimiter.clientIp(request);
        if (!rateLimiter.allow("rl:login:phone:" + req.getPhone(), LOGIN_PHONE_LIMIT, LOGIN_WINDOW_SECONDS)
                || !rateLimiter.allow("rl:login:ip:" + ip, LOGIN_IP_LIMIT, LOGIN_WINDOW_SECONDS)) {
            throw BusinessException.of(40027, "登录尝试过于频繁，请稍后再试");
        }
        return R.ok(authService.login(req));
    }

    /**
     * 登出：将当前令牌拉黑至剩余有效时长（幂等，无令牌也返回成功）
     * DELETE /app/auth/token
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/token")
    public R<Void> logout(@org.springframework.web.bind.annotation.RequestHeader(
            value = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
        return R.ok();
    }
}
