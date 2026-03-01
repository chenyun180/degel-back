package com.degel.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "degel.security")
public class DegelSecurityProperties {

    private String jwtSecret;
    private List<String> ignoreUrls = new ArrayList<>();
    /** 仅允许服务间 Feign 调用的路径，Gateway 直接拒绝外部访问 */
    private List<String> internalUrls = new ArrayList<>();
}
