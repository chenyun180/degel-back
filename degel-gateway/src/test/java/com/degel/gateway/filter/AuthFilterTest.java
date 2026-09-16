package com.degel.gateway.filter;

import com.degel.gateway.config.DegelSecurityProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthFilter 角色拦截单元测试。
 * Redis 黑名单用 Mockito mock（hasKey 恒为 false），测试 token 用与 filter 相同的 secret 签发。
 */
class AuthFilterTest {

    private static final String SECRET = "degel-jwt-secret-key-2024-platform-admin";
    private static final String APP_SECRET = "degel-c-end-secret-key-2024-change-in-prod";
    private static final String AUDIT_URL = "/product/spu/audit";

    private ReactiveStringRedisTemplate redisTemplate;
    private org.springframework.data.redis.core.ReactiveValueOperations<String, String> valueOps;
    private GatewayFilterChain chain;
    private AuthFilter authFilter;

    @BeforeEach
    void setUp() {
        DegelSecurityProperties properties = new DegelSecurityProperties();
        properties.setJwtSecret(SECRET);
        properties.setAppJwtSecret(APP_SECRET);
        properties.setIgnoreUrls(Collections.singletonList("/auth/oauth/token"));
        properties.setInternalUrls(Arrays.asList("/admin/user/find/", "/inner/"));
        properties.setAdminUrls(Arrays.asList(
                "*:/admin/user",
                "*:/admin/role",
                "*:/admin/menu",
                "*:/admin/shop",
                "PUT:/product/spu/audit",
                "POST:/product/category",
                "PUT:/product/category",
                "DELETE:/product/category"));
        properties.setAdminUrlExcludes(Arrays.asList(
                "GET:/admin/user/info",
                "*:/admin/shop/mine"));

        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
        valueOps = mock(org.springframework.data.redis.core.ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // token 版本查询缺省返回 empty（视作版本 0，不吊销）
        when(valueOps.get(anyString())).thenReturn(Mono.empty());

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        authFilter = new AuthFilter(properties, redisTemplate);
    }

    private String token(String jti, Long userId, String userName, Long shopId, java.util.List<String> roleKeys) {
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .setId(jti)
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000L))
                .claim("user_id", userId)
                .claim("user_name", userName)
                .claim("shop_id", shopId);
        if (roleKeys != null) {
            builder.claim("role_keys", roleKeys);
        }
        Key key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
        return builder.signWith(key, SignatureAlgorithm.HS256).compact();
    }

    private MockServerWebExchange exchange(HttpMethod method, String url, String token) {
        return exchange(method, url, token, null);
    }

    private MockServerWebExchange exchange(HttpMethod method, String url, String token, String forgedRoles) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(method, url);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (forgedRoles != null) {
            builder.header("X-User-Roles", forgedRoles);
        }
        return MockServerWebExchange.from(builder.build());
    }

    /** 场景 1：shop token 调 PUT /product/spu/audit → 403 */
    @Test
    void shopTokenOnAuditUrlIsForbidden() {
        MockServerWebExchange exchange = exchange(HttpMethod.PUT, AUDIT_URL,
                token("jti-1", 2L, "shopuser", 5L, Collections.singletonList("shop")));

        authFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    /** 场景 2：admin token 调同路径 → 放行 */
    @Test
    void adminTokenOnAuditUrlIsAllowed() {
        MockServerWebExchange exchange = exchange(HttpMethod.PUT, AUDIT_URL,
                token("jti-2", 1L, "adminuser", 0L, Collections.singletonList("admin")));

        authFilter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertEquals("admin", captor.getValue().getRequest().getHeaders().getFirst("X-User-Roles"));
    }

    /**
     * 场景 3a：外部请求自带伪造 X-User-Roles: admin + shop token 调 admin-url
     * → 伪造 header 被剥除，按真实角色拦截（403，且未放行到下游）。
     */
    @Test
    void forgedRolesHeaderIsStrippedAndShopStillForbidden() {
        MockServerWebExchange exchange = exchange(HttpMethod.PUT, AUDIT_URL,
                token("jti-3", 2L, "shopuser", 5L, Collections.singletonList("shop")), "admin");

        authFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    /**
     * 场景 3b：伪造 X-User-Roles: admin + shop token 调非 admin-url
     * → 放行，且下游收到的 X-User-Roles 是真实值 shop（伪造值被剥除后重写）。
     */
    @Test
    void forgedRolesHeaderIsOverwrittenWithRealRolesOnNonAdminUrl() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/product/spu/list",
                token("jti-4", 2L, "shopuser", 5L, Collections.singletonList("shop")), "admin");

        authFilter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders downstream = captor.getValue().getRequest().getHeaders();
        assertEquals("shop", downstream.getFirst("X-User-Roles"));
        assertFalse(downstream.get("X-User-Roles").contains("admin"));
    }

    /** 场景 4：无 role_keys 的旧式 token 调 admin-url → 403（有意强制重新登录） */
    @Test
    void legacyTokenWithoutRoleKeysIsForbiddenOnAdminUrl() {
        MockServerWebExchange exchange = exchange(HttpMethod.PUT, AUDIT_URL,
                token("jti-5", 1L, "olduser", 0L, null));

        authFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    /** 白名单路径（/auth/oauth/token）无 token → 放行 */
    @Test
    void whitelistedUrlWithoutTokenIsAllowed() {
        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/auth/oauth/token", null);

        authFilter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
        assertTrue(exchange.getResponse().getStatusCode() == null
                || exchange.getResponse().getStatusCode().is2xxSuccessful());
    }

    /** 场景 5a：shop token GET /product/category（不在 admin-urls 的 GET 侧）→ 放行 */
    @Test
    void shopTokenGetCategoryIsAllowed() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/product/category",
                token("jti-6", 2L, "shopuser", 5L, Collections.singletonList("shop")));

        authFilter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
        assertTrue(exchange.getResponse().getStatusCode() == null
                || exchange.getResponse().getStatusCode().is2xxSuccessful());
    }

    /** 场景 5b：shop token GET /admin/user（"*:/admin/user" 通配规则）→ 403 */
    @Test
    void shopTokenGetAdminUserIsForbidden() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/admin/user",
                token("jti-7", 2L, "shopuser", 5L, Collections.singletonList("shop")));

        authFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    /** 场景 6a：shop token GET /admin/user/info（admin-url-excludes 例外）→ 放行 */
    @Test
    void shopTokenGetUserInfoIsAllowedByExclude() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/admin/user/info",
                token("jti-8", 2L, "shopuser", 5L, Collections.singletonList("shop")));

        authFilter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
        assertTrue(exchange.getResponse().getStatusCode() == null
                || exchange.getResponse().getStatusCode().is2xxSuccessful());
    }

    /** 场景 6b：shop token PUT /admin/shop/mine（例外）→ 放行 */
    @Test
    void shopTokenPutShopMineIsAllowedByExclude() {
        MockServerWebExchange exchange = exchange(HttpMethod.PUT, "/admin/shop/mine",
                token("jti-9", 2L, "shopuser", 5L, Collections.singletonList("shop")));

        authFilter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
        assertTrue(exchange.getResponse().getStatusCode() == null
                || exchange.getResponse().getStatusCode().is2xxSuccessful());
    }

    /** 场景 6c：shop token POST /admin/user（例外未放开创建用户）→ 仍 403 */
    @Test
    void shopTokenPostAdminUserStillForbidden() {
        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/admin/user",
                token("jti-10", 2L, "shopuser", 5L, Collections.singletonList("shop")));

        authFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    /** 场景 6d：shop token GET /admin/user/list（例外不覆盖）→ 仍 403 */
    @Test
    void shopTokenGetAdminUserListStillForbidden() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/admin/user/list",
                token("jti-11", 2L, "shopuser", 5L, Collections.singletonList("shop")));

        authFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    /** 场景 6e：admin token POST /admin/user → 仍放行 */
    @Test
    void adminTokenPostAdminUserIsAllowed() {
        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/admin/user",
                token("jti-12", 1L, "adminuser", 0L, Collections.singletonList("admin")));

        authFilter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
        assertTrue(exchange.getResponse().getStatusCode() == null
                || exchange.getResponse().getStatusCode().is2xxSuccessful());
    }

    /** c_end 令牌（与 degel-app 相同密钥签发，type=c_end，sub=userId） */
    private String cEndToken(String jti, Long userId) {
        Key key = new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
        return Jwts.builder()
                .setId(jti)
                .setSubject(String.valueOf(userId))
                .claim("type", "c_end")
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000L))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** 场景 7a：c_end token 访问平台券管理（known-issues 记录的穿透路径）→ 403 */
    @Test
    void cEndTokenOnPlatformCouponUrlIsForbidden() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/marketing/platform/coupon/list",
                cEndToken("jti-app-1", 1L));

        authFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    /** 场景 7b：c_end token 访问 admin-urls（/admin/user）→ 403 */
    @Test
    void cEndTokenOnAdminUrlIsForbidden() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/admin/user/list",
                cEndToken("jti-app-2", 1L));

        authFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    /** 场景 7c：c_end token 访问任意非 /app 路径 → 403（/marketing/shop 穿透一并根治） */
    @Test
    void cEndTokenOnNonAppPathIsForbidden() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/order/shop/list",
                cEndToken("jti-app-3", 1L));

        authFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    /** 场景 7d：c_end token 访问 /app 路径 → 放行，X-User-Id 注入 sub */
    @Test
    void cEndTokenOnAppPathIsAllowedWithUserIdInjected() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/app/coupon/mine",
                cEndToken("jti-app-4", 42L));

        authFilter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertEquals("42", captor.getValue().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("0", captor.getValue().getRequest().getHeaders().getFirst("X-Shop-Id"));
    }

    /** 场景 8a：与白名单规则同前缀的路径（/auth/oauth/token-xxx）不再被 startsWith 误放行 → 无 token 401 */
    @Test
    void pathSharingPrefixWithWhitelistRuleIsNotWhitelisted() {
        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/auth/oauth/token-xxx", null);

        authFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    /** 场景 8b：/app/ 前缀规则不匹配 /appx → 无 token 401（独立 properties，含 /app/ 白名单） */
    @Test
    void appPrefixRuleDoesNotMatchSimilarSegment() {
        DegelSecurityProperties appProps = new DegelSecurityProperties();
        appProps.setJwtSecret(SECRET);
        appProps.setAppJwtSecret(APP_SECRET);
        appProps.setIgnoreUrls(Arrays.asList("/auth/oauth/token", "/app/"));
        AuthFilter appFilter = new AuthFilter(appProps, redisTemplate);

        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/appx", null);

        appFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    /** 管理端 token 构造（带 token_version claim，用于用户级吊销用例） */
    private String tokenWithVersion(Long userId, long tokenVersion) {
        Key key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
        return Jwts.builder()
                .setId("jti-ver-" + tokenVersion)
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000L))
                .claim("user_id", userId)
                .claim("user_name", "shopuser")
                .claim("shop_id", 5L)
                .claim("role_keys", Collections.singletonList("shop"))
                .claim("token_version", tokenVersion)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** 场景 9a：Redis 当前版本(6) > claim(5)（改密/禁用后 INCR）→ 旧 token 401 */
    @Test
    void staleTokenVersionIsRejected() {
        when(valueOps.get(org.mockito.ArgumentMatchers.startsWith("auth:tokenver:")))
                .thenReturn(Mono.just("6"));
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/product/spu/list",
                tokenWithVersion(2L, 5L));

        authFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    /** 场景 9b：claim(5) >= 当前版本(5) → 放行 */
    @Test
    void currentTokenVersionIsAllowed() {
        when(valueOps.get(org.mockito.ArgumentMatchers.startsWith("auth:tokenver:")))
                .thenReturn(Mono.just("5"));
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/product/spu/list",
                tokenWithVersion(2L, 5L));

        authFilter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
        assertTrue(exchange.getResponse().getStatusCode() == null
                || exchange.getResponse().getStatusCode().is2xxSuccessful());
    }
}
