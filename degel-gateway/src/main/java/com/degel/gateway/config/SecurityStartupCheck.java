package com.degel.gateway.config;

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
 * <p>网关用 HS256 对称密钥双端（auth/网关）验签，密钥带默认值上线等于
 * "任何读过仓库的人都能伪造带 role_keys:["admin"] 的 token"，必须机制性拦截。
 * 与 degel-auth 的 SecurityStartupCheck 配套，生产部署必须以 prod profile 启动。
 */
@Slf4j
@Component
public class SecurityStartupCheck {

    private static final List<String> KNOWN_DEFAULTS = Arrays.asList(
            "degel-jwt-secret-key-2024-platform-admin",
            "degel-c-end-secret-key-2024-change-in-prod");

    private final Environment environment;
    private final DegelSecurityProperties properties;

    public SecurityStartupCheck(Environment environment, DegelSecurityProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @PostConstruct
    public void check() {
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        checkOne(prod, "degel.security.jwt-secret", properties.getJwtSecret());
        checkOne(prod, "degel.security.app-jwt-secret", properties.getAppJwtSecret());
    }

    private void checkOne(boolean prod, String name, String value) {
        if (value == null || !KNOWN_DEFAULTS.contains(value)) {
            return;
        }
        if (prod) {
            throw new IllegalStateException(String.format(
                    "[%s] 在 prod profile 下仍是默认值（%s）——伪造 token 的直接风险，"
                            + "请用环境变量注入强值后重启。本地开发请去掉 prod profile。",
                    name, value));
        }
        log.warn("========== 安全警告 ==========");
        log.warn("[{}] 使用默认值，仅限本地开发！生产必须用环境变量覆盖，且以 prod profile 启动（默认值会拒绝启动）", name);
        log.warn("============================");
    }
}
