package com.degel.app.vo.dto;

import lombok.Data;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 编辑收货地址请求 VO
 * PUT /app/user/address/{id}
 * 所有字段可选，非空字段才更新
 */
@Data
public class AddressUpdateReqVO {

    /**
     * 收货人姓名（可选更新）
     */
    @Size(max = 32, message = "收货人姓名不超过32字符")
    private String name;

    /**
     * 手机号（可选更新）
     */
    @Pattern(regexp = "^(1[3-9]\\d{9})?$", message = "手机号格式不正确")
    private String phone;

    /**
     * 省（可选更新）
     */
    private String province;

    /**
     * 市（可选更新）
     */
    private String city;

    /**
     * 区/县（可选更新）
     */
    private String district;

    /**
     * 详细地址（可选更新）
     */
    @Size(max = 100, message = "详细地址不超过100字符")
    private String detail;

    // 注意：isDefault 字段不在此处，编辑接口不允许修改默认状态
    // 请使用 PUT /app/user/address/{id}/default 接口设置默认
}
