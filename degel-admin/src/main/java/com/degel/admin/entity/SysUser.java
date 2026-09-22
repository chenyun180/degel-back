package com.degel.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.degel.common.core.BaseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;
    /** 序列化层兜底（低危修复）：只进不出，防止新接口漏写 setPassword(null) 直出 BCrypt 哈希 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String nickname;
    private String phone;
    private String email;
    private String avatar;
    private Integer status;
    private Long shopId;
}
