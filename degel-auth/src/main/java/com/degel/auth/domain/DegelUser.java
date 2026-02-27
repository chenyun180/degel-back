package com.degel.auth.domain;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

@Getter
public class DegelUser extends User {

    private final Long userId;
    private final Long shopId;

    public DegelUser(Long userId, String username, String password, Long shopId,
                     Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.userId = userId;
        this.shopId = shopId;
    }

    public DegelUser(Long userId, String username, String password, Long shopId,
                     boolean enabled, Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, true, authorities);
        this.userId = userId;
        this.shopId = shopId;
    }
}
