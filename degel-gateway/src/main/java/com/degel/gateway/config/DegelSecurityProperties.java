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
    /** 仅允许超管访问的路径，格式 METHOD:PATH 前缀，METHOD 为 * 表示任意方法 */
    private List<String> adminUrls = new ArrayList<>();
    /** admin-urls 的例外（自服务接口），格式同 admin-urls，命中则不做 admin 角色校验 */
    private List<String> adminUrlExcludes = new ArrayList<>();
}
