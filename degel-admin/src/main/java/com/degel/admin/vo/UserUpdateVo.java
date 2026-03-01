package com.degel.admin.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

@Data
public class UserUpdateVo implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "用户ID不能为空")
    private Long id;

    private String nickname;
    private String phone;
    private String email;
    private Integer status;
    private Long shopId;
    private List<Long> roleIds;
}
