package com.degel.app.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.degel.app.context.UserContext;
import com.degel.app.service.FootprintService;
import com.degel.app.vo.FootprintVO;
import com.degel.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 浏览足迹 Controller（/app/footprint 不在公开前缀，AppSecurityFilter 强制 JWT）
 */
@RestController
@RequestMapping("/app/footprint")
@RequiredArgsConstructor
public class FootprintController {

    private final FootprintService footprintService;

    /** GET /app/footprint/list — 足迹列表（按浏览时间倒序，前端按今天/昨天/7天内分组） */
    @GetMapping("/list")
    public R<IPage<FootprintVO>> list(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        if (pageSize > 50) {
            pageSize = 50;
        }
        return R.ok(footprintService.list(UserContext.getUserId(), page, pageSize));
    }

    /** DELETE /app/footprint/item?spuId= — 删除单条足迹 */
    @DeleteMapping("/item")
    public R<Void> remove(@RequestParam("spuId") Long spuId) {
        footprintService.remove(UserContext.getUserId(), spuId);
        return R.ok(null);
    }

    /** DELETE /app/footprint/clear — 清空足迹 */
    @DeleteMapping("/clear")
    public R<Void> clear() {
        footprintService.clear(UserContext.getUserId());
        return R.ok(null);
    }
}
