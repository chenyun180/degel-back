package com.degel.admin.vo;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class RouterVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String path;
    private String component;
    private Boolean hidden;
    private String redirect;
    private MetaVo meta;
    private List<RouterVo> children;
}
