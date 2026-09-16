package com.degel.auth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

/**
 * 启动期密钥校验：prod profile 下命中已知默认值直接拒绝启动（fail-fast），
 * 非 prod 命中默认值打显著 WARN。
 *
 * <p>此前默认密钥只靠"生产记得注入环境变量"约束（known-issues 有记录但无机制保证），
 * HS256 对称密钥一旦带默认值上线，任何读过仓库的人都能伪造 admin token。
 * 生产部署约定：必须带 --spring.profiles.active=prod 启动。
 */
@Slf4j
@Component
public class SecurityStartupCheck {

    private static final List<String> KNOWN_DEFAULTS = Arrays.asList(
            "degel-jwt-secret-key-2024-platform-admin",
            "degel",
            "degel_secret");

    private final Environment environment;
    private final String jwtSecret;
    private final String clientId;
    private final String clientSecret;

    public SecurityStartupCheck(Environment environment,
                                @org.springframework.beans.factory.annotation.Value("${degel.security.jwt-secret}") String jwtSecret,
                                @org.springframework.beans.factory.annotation.Value("${degel.oauth.client-id}") String clientId,
                                @org.springframework.beans.factory.annotation.Value("${degel.oauth.client-secret}") String clientSecret) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @PostConstruct
    public void check() {
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        checkOne(prod, "degel.security.jwt-secret", jwtSecret);
        checkOne(prod, "degel.oauth.client-id", clientId);
        checkOne(prod, "degel.oauth.client-secret", clientSecret);
    }

    private void checkOne(boolean prod, String name, String value) {
        if (!KNOWN_DEFAULTS.contains(value)) {
            return;
        }
        if (prod) {
            throw new IllegalStateException(String.format(
                    "[%s] 在 prod profile 下仍是默认值（%s）——伪造 token / 冒充客户端的直接风险，"
                            + "请用环境变量注入强值后重启。本地开发请去掉 prod profile。",
                    name, value));
        }
        log.warn("========== 安全警告 ==========");
        log.warn("[{}] 使用默认值，仅限本地开发！生产必须用环境变量覆盖，且以 prod profile 启动（默认值会拒绝启动）", name);
        log.warn("============================");
    }
}
