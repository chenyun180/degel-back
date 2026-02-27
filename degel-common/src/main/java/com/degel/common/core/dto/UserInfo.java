package com.degel.common.core.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class UserInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String password;
    private String nickname;
    private Integer status;
    private Long shopId;
    private List<String> roles;
    private List<String> permissions;
}
