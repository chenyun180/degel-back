package com.degel.auth.enhancer;

import com.degel.auth.domain.DegelUser;
import com.degel.common.core.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 令牌增强：写入网关鉴权所需的 claims。
 *
 * <p>token_version（用户级吊销版本）：取 Redis auth:tokenver:{userId} 当前值（缺省 0）。
 * 改密/禁用/删除用户/停店铺时 admin 侧 INCR 该 key，网关比对 claim &lt; 当前值即 401，
 * 实现按用户维度的全量 token 吊销（jti 黑名单只能吊销单个 token）。
 * Redis 读取失败时按 0 处理并记日志——签发侧保守，吊销不生效但登录不受阻（fail-open，
 * 与网关侧语义一致：Redis 故障不应阻断整个登录链路）。
 */
@Slf4j
@RequiredArgsConstructor
public class CustomTokenEnhancer implements TokenEnhancer {

    private static final String ROLE_PREFIX = "ROLE_";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public OAuth2AccessToken enhance(OAuth2AccessToken accessToken, OAuth2Authentication authentication) {
        if (authentication.getUserAuthentication() != null) {
            Object principal = authentication.getUserAuthentication().getPrincipal();
            if (principal instanceof DegelUser) {
                DegelUser user = (DegelUser) principal;
                Map<String, Object> additionalInfo = new HashMap<>(8);
                additionalInfo.put("user_id", user.getUserId());
                additionalInfo.put("shop_id", user.getShopId());
                // 修复：user_name 此前无写入方，网关 X-User-Name 恒为空
                additionalInfo.put("user_name", user.getUsername());
                // 新增：角色标识列表（去掉 ROLE_ 前缀），供网关做角色校验
                List<String> roleKeys = user.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(a -> a.startsWith(ROLE_PREFIX))
                        .map(a -> a.substring(ROLE_PREFIX.length()))
                        .collect(Collectors.toList());
                additionalInfo.put("role_keys", roleKeys);
                additionalInfo.put("token_version", currentTokenVersion(user.getUserId()));
                ((DefaultOAuth2AccessToken) accessToken).setAdditionalInformation(additionalInfo);
            }
        }
        return accessToken;
    }

    private long currentTokenVersion(Long userId) {
        if (userId == null) {
            return 0L;
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(Constants.AUTH_TOKEN_VERSION_PREFIX + userId);
            return value != null ? Long.parseLong(value) : 0L;
        } catch (Exception e) {
            log.warn("Read token version failed (treat as 0): {}", e.getMessage());
            return 0L;
        }
    }
}
