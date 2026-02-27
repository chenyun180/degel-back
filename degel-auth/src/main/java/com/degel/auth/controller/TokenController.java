package com.degel.auth.controller;

import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/token")
@RequiredArgsConstructor
public class TokenController {

    private final TokenStore tokenStore;

    @DeleteMapping
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String tokenValue = authHeader.substring(7);
            OAuth2AccessToken accessToken = tokenStore.readAccessToken(tokenValue);
            if (accessToken != null) {
                tokenStore.removeAccessToken(accessToken);
            }
        }
        return R.ok();
    }
}
