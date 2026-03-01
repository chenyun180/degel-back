package com.degel.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.admin.entity.SysUser;
import com.degel.admin.service.ISysMenuService;
import com.degel.admin.service.ISysUserService;
import com.degel.admin.vo.UserCreateVo;
import com.degel.admin.vo.UserUpdateVo;
import com.degel.common.core.R;
import com.degel.common.core.dto.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class SysUserController {

    private final ISysUserService userService;
    private final ISysMenuService menuService;

    @GetMapping("/find/{username}")
    public R<UserInfo> findByUsername(@PathVariable String username) {
        UserInfo userInfo = userService.getUserInfoByUsername(username);
        if (userInfo == null) {
            return R.fail("用户不存在");
        }
        return R.ok(userInfo);
    }

    @GetMapping("/info")
    public R<Map<String, Object>> getCurrentUserInfo(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        SysUser user = userService.getById(userId);
        if (user == null) {
            return R.fail("用户不存在");
        }
        user.setPassword(null);

        Map<String, Object> result = new HashMap<>(4);
        result.put("user", user);
        result.put("roles", userService.getRoleKeysByUserId(userId));
        result.put("permissions", menuService.getPermsByUserId(userId));
        result.put("routers", menuService.getRoutersByUserId(userId));
        return R.ok(result);
    }

    @GetMapping("/list")
    public R<IPage<SysUser>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            SysUser query) {
        IPage<SysUser> page = userService.pageUsers(new Page<>(current, size), query);
        page.getRecords().forEach(u -> u.setPassword(null));
        return R.ok(page);
    }

    @PostMapping
    public R<Void> create(@Valid @RequestBody UserCreateVo vo) {
        SysUser user = new SysUser();
        user.setUsername(vo.getUsername());
        user.setPassword(vo.getPassword());
        user.setNickname(vo.getNickname());
        user.setPhone(vo.getPhone());
        user.setEmail(vo.getEmail());
        user.setStatus(vo.getStatus() != null ? vo.getStatus() : 0);
        user.setShopId(vo.getShopId() != null ? vo.getShopId() : 0L);
        userService.createUser(user, vo.getRoleIds());
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@Valid @RequestBody UserUpdateVo vo) {
        SysUser user = new SysUser();
        user.setId(vo.getId());
        user.setNickname(vo.getNickname());
        user.setPhone(vo.getPhone());
        user.setEmail(vo.getEmail());
        user.setStatus(vo.getStatus());
        user.setShopId(vo.getShopId());
        userService.updateUser(user, vo.getRoleIds());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return R.ok();
    }
}
