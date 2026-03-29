package com.degel.app.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录成功响应 VO（微信登录 & H5登录 共用）
 */
@Data
@Builder
public class WxLoginVO {

    /** C端 JWT Token */
    private String token;

    /** 用户ID */
    private Long userId;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;
}
