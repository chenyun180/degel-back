package com.degel.auth.service;

import com.degel.auth.domain.DegelUser;
import com.degel.auth.feign.RemoteUserService;
import com.degel.common.core.R;
import com.degel.common.core.dto.UserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final RemoteUserService remoteUserService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        R<UserInfo> result = remoteUserService.findByUsername(username);
        if (result == null || result.getCode() != 200 || result.getData() == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        UserInfo userInfo = result.getData();
        boolean enabled = userInfo.getStatus() != null && userInfo.getStatus() == 0;

        Set<GrantedAuthority> authorities = new HashSet<>();
        if (userInfo.getRoles() != null) {
            for (String role : userInfo.getRoles()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        }
        if (userInfo.getPermissions() != null) {
            for (String perm : userInfo.getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(perm));
            }
        }

        return new DegelUser(
                userInfo.getUserId(),
                userInfo.getUsername(),
                userInfo.getPassword(),
                userInfo.getShopId(),
                enabled,
                authorities
        );
    }
}
