package com.degel.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String title;
    private String icon;
}
