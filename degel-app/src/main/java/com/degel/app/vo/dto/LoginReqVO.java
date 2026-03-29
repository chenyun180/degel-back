package com.degel.app.vo.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * H5 账号密码登录请求 VO
 * POST /app/auth/login
 */
@Data
public class LoginReqVO {

    /**
     * 手机号
     */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /**
     * 明文密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
