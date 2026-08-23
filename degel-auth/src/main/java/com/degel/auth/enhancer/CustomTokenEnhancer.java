package com.degel.auth.enhancer;

import com.degel.auth.domain.DegelUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CustomTokenEnhancer implements TokenEnhancer {

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public OAuth2AccessToken enhance(OAuth2AccessToken accessToken, OAuth2Authentication authentication) {
        if (authentication.getUserAuthentication() != null) {
            Object principal = authentication.getUserAuthentication().getPrincipal();
            if (principal instanceof DegelUser) {
                DegelUser user = (DegelUser) principal;
                Map<String, Object> additionalInfo = new HashMap<>(4);
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
                ((DefaultOAuth2AccessToken) accessToken).setAdditionalInformation(additionalInfo);
            }
        }
        return accessToken;
    }
}
