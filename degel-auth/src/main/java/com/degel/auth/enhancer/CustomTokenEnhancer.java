package com.degel.auth.enhancer;

import com.degel.auth.domain.DegelUser;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;

import java.util.HashMap;
import java.util.Map;

public class CustomTokenEnhancer implements TokenEnhancer {

    @Override
    public OAuth2AccessToken enhance(OAuth2AccessToken accessToken, OAuth2Authentication authentication) {
        if (authentication.getUserAuthentication() != null) {
            Object principal = authentication.getUserAuthentication().getPrincipal();
            if (principal instanceof DegelUser) {
                DegelUser user = (DegelUser) principal;
                Map<String, Object> additionalInfo = new HashMap<>(4);
                additionalInfo.put("user_id", user.getUserId());
                additionalInfo.put("shop_id", user.getShopId());
                ((DefaultOAuth2AccessToken) accessToken).setAdditionalInformation(additionalInfo);
            }
        }
        return accessToken;
    }
}
