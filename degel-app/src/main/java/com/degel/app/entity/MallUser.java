package com.degel.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * C端用户实体
 * 对应数据库表 mall_user（degel_app 库）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_user")
public class MallUser extends BaseEntity {

    /** 微信 openid，H5 用户为空 */
    private String openid;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 手机号，微信用户可为空 */
    private String phone;

    /** 密码（BCrypt 存储），微信用户为空 */
    private String password;

    /**
     * 账号状态
     * 0 = 正常
     * 1 = 封禁
     */
    private Integer status;
}
