package com.degel.auth.feign;

import com.degel.common.core.R;
import com.degel.common.core.dto.UserInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "degel-admin", contextId = "remoteUserService")
public interface RemoteUserService {

    @GetMapping("/user/find/{username}")
    R<UserInfo> findByUsername(@PathVariable("username") String username);
}
