package com.degel.app.vo.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

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
}
