package com.degel.app.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.context.UserContext;
import com.degel.app.service.FavoriteService;
import com.degel.app.vo.FavoriteVO;
import com.degel.common.core.R;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * 商品收藏 Controller（/app/favorite 不在公开前缀，AppSecurityFilter 强制 JWT）
 */
@RestController
@RequestMapping("/app/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /** POST /app/favorite — 收藏商品（幂等） */
    @PostMapping
    public R<Void> add(@Valid @RequestBody FavoriteReqVO reqVO) {
        favoriteService.add(UserContext.getUserId(), reqVO.getSpuId());
        return R.ok(null);
    }

    /** DELETE /app/favorite?spuId= — 取消收藏（幂等） */
    @DeleteMapping
    public R<Void> remove(@RequestParam("spuId") Long spuId) {
        favoriteService.remove(UserContext.getUserId(), spuId);
        return R.ok(null);
    }

    /** GET /app/favorite/list — 收藏列表（按收藏时间倒序） */
    @GetMapping("/list")
    public R<IPage<FavoriteVO>> list(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        if (pageSize > 50) {
            pageSize = 50;
        }
        return R.ok(favoriteService.list(UserContext.getUserId(), page, pageSize));
    }

    /** GET /app/favorite/check?spuId= — 是否已收藏（详情页按钮状态） */
    @GetMapping("/check")
    public R<Boolean> check(@RequestParam("spuId") Long spuId) {
        return R.ok(favoriteService.isFavorite(UserContext.getUserId(), spuId));
    }

    @Data
    public static class FavoriteReqVO {
        @NotNull(message = "商品ID不能为空")
        private Long spuId;
    }
}
