package com.degel.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.admin.entity.SysUser;
import com.degel.admin.service.ISysMenuService;
import com.degel.admin.service.ISysUserService;
import com.degel.admin.vo.RouterVo;
import com.degel.common.core.R;
import com.degel.common.core.dto.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class SysUserController {

    private final ISysUserService userService;
    private final ISysMenuService menuService;

    /** Auth 服务内部调用 - 根据用户名获取用户信息 */
    @GetMapping("/find/{username}")
    public R<UserInfo> findByUsername(@PathVariable String username) {
        UserInfo userInfo = userService.getUserInfoByUsername(username);
        if (userInfo == null) {
            return R.fail("用户不存在");
        }
        return R.ok(userInfo);
    }

    /** 获取当前登录用户信息（前端调用） */
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
    public R<Void> create(@RequestBody Map<String, Object> params) {
        SysUser user = new SysUser();
        user.setUsername((String) params.get("username"));
        user.setPassword((String) params.get("password"));
        user.setNickname((String) params.get("nickname"));
        user.setPhone((String) params.get("phone"));
        user.setEmail((String) params.get("email"));
        user.setStatus(params.get("status") != null ? (Integer) params.get("status") : 0);
        user.setShopId(params.get("shopId") != null ? Long.valueOf(params.get("shopId").toString()) : 0L);

        @SuppressWarnings("unchecked")
        List<Long> roleIds = (List<Long>) params.get("roleIds");
        userService.createUser(user, roleIds);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody Map<String, Object> params) {
        SysUser user = new SysUser();
        user.setId(Long.valueOf(params.get("id").toString()));
        user.setNickname((String) params.get("nickname"));
        user.setPhone((String) params.get("phone"));
        user.setEmail((String) params.get("email"));
        user.setStatus(params.get("status") != null ? (Integer) params.get("status") : null);
        user.setShopId(params.get("shopId") != null ? Long.valueOf(params.get("shopId").toString()) : null);

        @SuppressWarnings("unchecked")
        List<Long> roleIds = (List<Long>) params.get("roleIds");
        userService.updateUser(user, roleIds);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return R.ok();
    }
}
