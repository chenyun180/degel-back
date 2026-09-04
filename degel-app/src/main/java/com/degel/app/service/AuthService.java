package com.degel.app.service;

import com.degel.app.vo.WxLoginVO;
import com.degel.app.vo.dto.LoginReqVO;
import com.degel.app.vo.dto.WxLoginReqVO;

/**
 * 认证 Service 接口
 */
public interface AuthService {

    /**
     * 微信小程序登录
     * 1. 调用微信 jscode2session 换取 openid
     * 2. 查/插 mall_user
     * 3. 签发 C端 JWT
     */
    WxLoginVO wxLogin(WxLoginReqVO req);

    /**
     * H5 账号密码登录
     * 1. 以 phone 查询用户
     * 2. BCrypt 校验密码
     * 3. 签发 C端 JWT
     */
    WxLoginVO login(LoginReqVO req);

    /**
     * 登出：将当前令牌的 jti 写入 Redis 黑名单至剩余有效时长
     * @param authorizationHeader 请求头 Authorization（Bearer xxx），缺失时静默成功
     */
    void logout(String authorizationHeader);
}
