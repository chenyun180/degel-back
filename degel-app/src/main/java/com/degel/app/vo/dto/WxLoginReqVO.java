package com.degel.app.vo.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 微信登录请求 VO
 * POST /app/auth/wx-login
 */
@Data
public class WxLoginReqVO {

    /**
     * 微信 wx.login() 返回的临时授权码
     */
    @NotBlank(message = "code 不能为空")
    private String code;

    /**
     * 用户昵称
     */
    @NotBlank(message = "昵称不能为空")
    private String nickname;

    /**
     * 头像 URL（可选）
     */
    private String avatar;

    /**
     * 手机号（可选）。提供且未注册时写入 mall_user.phone，
     * 使该微信账号后续可用 H5 手机号+密码登录打通同一账户；
     * 缺省则 phone 为空，暂不阻断登录（账号绑定可后补）。
     */
    @Pattern(regexp = "^(1[3-9]\\d{9})?$", message = "手机号格式不正确")
    private String phone;
}
