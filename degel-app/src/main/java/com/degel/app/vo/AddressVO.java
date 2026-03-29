package com.degel.app.vo;

import lombok.Data;

/**
 * 收货地址 VO（Feign 从 degel-app 自身地址表获取，或从 degel-order 获取）
 */
@Data
public class AddressVO {

    private Long id;
    private Long userId;
    private String name;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;
    /** 是否默认：0否 1是 */
    private Integer isDefault;

    /**
     * 拼接全地址（省+市+区+详细）
     */
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        if (province != null) sb.append(province);
        if (city != null) sb.append(city);
        if (district != null) sb.append(district);
        if (detail != null) sb.append(detail);
        return sb.toString();
    }
}
