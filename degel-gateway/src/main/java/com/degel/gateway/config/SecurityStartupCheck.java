package com.degel.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

/**
 * 启动期密钥校验：严格模式（2026-09-20 安全扫描 H3 修复后配套强化）。
 *
 * <p>bootstrap.yml 已去掉密钥默认值（占位符形如 ${JWT_SECRET}），但
 * {@code @ConfigurationProperties} 的 Binder 对解析失败的占位符是**静默保留字面量**
 * （如 "${JWT_SECRET}"），不会抛错——不在这里显式拦截的话，网关会拿着公开的
 * 字面量字符串当 HS256 密钥，比报错更危险。
 *
 * <p>因此空值 / 未解析占位符 / 已知默认值一律拒绝启动，不再区分 profile；
 * 本地开发值由根目录 degel.sh 统一导出。
 *
 * <p>2026-09-22 低危修复追加：CORS 白名单在 prod profile 下不允许使用含通配符的
 * 开发默认值（192.168.1.* 等）——allowCredentials(true) + 通配 origin 等于局域网
 * 任意页面可携凭证跨域。生产必须用 DEGEL_CORS_ORIGINS 配置真实域名。
 */
@Slf4j
@Component
public class SecurityStartupCheck {

    private static final List<String> KNOWN_DEFAULTS = Arrays.asList(
            "degel-jwt-secret-key-2024-platform-admin",
            "degel-c-end-secret-key-2024-change-in-prod");

    private final DegelSecurityProperties properties;
    private final Environment environment;
    private final String corsOrigins;

    public SecurityStartupCheck(DegelSecurityProperties properties,
                                Environment environment,
                                @Value("${degel.cors.origins:}") String corsOrigins) {
        this.properties = properties;
        this.environment = environment;
        this.corsOrigins = corsOrigins;
    }

    @PostConstruct
    public void check() {
        checkOne("degel.security.jwt-secret", properties.getJwtSecret());
        checkOne("degel.security.app-jwt-secret", properties.getAppJwtSecret());
        checkCors();
    }

    private void checkOne(String name, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(String.format(
                    "[%s] 未配置（空值）——必须通过环境变量注入强值，拒绝启动", name));
        }
        if (value.startsWith("${") && value.endsWith("}")) {
            throw new IllegalStateException(String.format(
                    "[%s] 占位符未解析（字面量 %s）——环境变量缺失，Binder 已静默保留字面量，"
                            + "继续启动等于用公开字符串当签名密钥，拒绝启动", name, value));
        }
        if (KNOWN_DEFAULTS.contains(value)) {
            throw new IllegalStateException(String.format(
                    "[%s] 仍是已知默认值（%s）——请注入强值后重启，拒绝启动", name, value));
        }
        log.info("[SecurityStartupCheck] {} 已配置（严格模式校验通过）", name);
    }

    /** prod 下 CORS 白名单含通配符（开发默认值特征）即拒绝启动；非 prod 仅提示 */
    private void checkCors() {
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        if (corsOrigins != null && corsOrigins.contains("*")) {
            if (prod) {
                throw new IllegalStateException(String.format(
                        "[degel.cors.origins] prod profile 下含通配符（%s）——allowCredentials(true) "
                                + "配合通配 origin 允许任意匹配页面携凭证跨域，必须用 DEGEL_CORS_ORIGINS "
                                + "配置显式真实域名，拒绝启动", corsOrigins));
            }
            log.warn("[SecurityStartupCheck] degel.cors.origins 含通配符（开发默认值），生产必须用 DEGEL_CORS_ORIGINS 覆盖");
        }
    }
}
