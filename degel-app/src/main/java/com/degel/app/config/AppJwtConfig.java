package com.degel.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * C端 JWT 配置
 * 对应 bootstrap.yml 中的 degel.app.jwt 节点
 */
@Data
@Component
@ConfigurationProperties(prefix = "degel.app.jwt")
public class AppJwtConfig {

    /**
     * JWT 签名密钥
     */
    private String secret;

    /**
     * JWT 有效期（秒），默认7天=604800
     */
    private Long expiration;
}
