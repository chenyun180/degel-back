package com.degel.app.vo.dto;

import lombok.Data;

import java.util.List;

/**
 * 秒杀场次 DTO（Feign 反序列化 marketing SeckillSessionVo）。
 * Long id 经 Jackson 反序列化为 Long（marketing 侧序列化为字符串，双向兼容）；
 * 时间收 String（"yyyy-MM-dd HH:mm:ss"，marketing 服务器本地时区，同机部署无差），
 * 由 BFF 统一解析为 epoch ms。
 */
@Data
public class SeckillSessionDTO {

    private Long id;
    private String name;
    private String startTime;
    private String endTime;
    /** 场次人工启停：0=停用 1=启用 */
    private Integer status;
    private Integer sort;
    private List<SeckillProductDTO> products;
}
