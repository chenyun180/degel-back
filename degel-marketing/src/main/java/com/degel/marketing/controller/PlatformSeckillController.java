package com.degel.marketing.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.common.core.R;
import com.degel.marketing.entity.SeckillProduct;
import com.degel.marketing.entity.SeckillSession;
import com.degel.marketing.service.SeckillProductService;
import com.degel.marketing.service.SeckillSessionService;
import com.degel.marketing.vo.SeckillProductCreateVo;
import com.degel.marketing.vo.SeckillProductVo;
import com.degel.marketing.vo.SeckillSessionCreateVo;
import com.degel.marketing.vo.SeckillSessionVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.stream.Collectors;

/**
 * 平台秒杀场次/商品管理（经网关 /marketing/platform/**，admin-urls 限平台角色）。
 *
 * 所有写操作额外校验 X-Shop-Id==0（店铺工作台 shopId>0 拒绝），秒杀是平台侧营销能力。
 * 网关安全：AuthFilter 已将 c_end 令牌限制在 /app/**，本 controller 不依赖此弱兜底作唯一防线。
 */
@RestController
@RequestMapping("/platform/seckill")
@RequiredArgsConstructor
public class PlatformSeckillController {

    private final SeckillSessionService seckillSessionService;
    private final SeckillProductService seckillProductService;

    // ------------------------------------------------------------------
    // 场次
    // ------------------------------------------------------------------

    @GetMapping("/session/page")
    public R<IPage<SeckillSessionVo>> sessionPage(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long pageSize,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "status", required = false) Integer status) {
        IPage<SeckillSession> result = seckillSessionService.page(page, pageSize, name, status);
        Page<SeckillSessionVo> vo = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        vo.setRecords(result.getRecords().stream().map(SeckillSessionVo::from).collect(Collectors.toList()));
        return R.ok(vo);
    }

    /** 场次新增/编辑（vo.id 空=新增） */
    @PostMapping("/session")
    public R<Void> saveOrUpdateSession(
            @Valid @RequestBody SeckillSessionCreateVo vo,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        if (shopId == null || shopId > 0) {
            return R.fail("仅平台可管理秒杀场次");
        }
        seckillSessionService.saveOrUpdateSession(vo);
        return R.ok();
    }

    /** 场次逻辑删除 */
    @DeleteMapping("/session/{id}")
    public R<Void> deleteSession(
            @PathVariable Long id,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        if (shopId == null || shopId > 0) {
            return R.fail("仅平台可管理秒杀场次");
        }
        seckillSessionService.delete(id);
        return R.ok();
    }

    /** 场次启/停取反（1↔0） */
    @PutMapping("/session/toggle-status/{id}")
    public R<Void> toggleSessionStatus(
            @PathVariable Long id,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        if (shopId == null || shopId > 0) {
            return R.fail("仅平台可管理秒杀场次");
        }
        seckillSessionService.toggleStatus(id);
        return R.ok();
    }

    /**
     * 重新预热：清除该场次 Redis 预热数据，下个预热周期（约 1 分钟，或首次 reserve 懒预热）
     * 按 DB 最新配置重建。仅启用且未开始的场次允许——已开场次重置会把已售量重新放出（超卖）。
     */
    @PutMapping("/session/rewarm/{id}")
    public R<Void> rewarmSession(
            @PathVariable Long id,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        if (shopId == null || shopId > 0) {
            return R.fail("仅平台可管理秒杀场次");
        }
        seckillSessionService.rewarm(id);
        return R.ok();
    }

    // ------------------------------------------------------------------
    // 场次商品
    // ------------------------------------------------------------------

    @GetMapping("/product/page")
    public R<IPage<SeckillProductVo>> productPage(
            @RequestParam Long sessionId,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long pageSize) {
        IPage<SeckillProduct> result = seckillProductService.pageBySession(sessionId, page, pageSize);
        Page<SeckillProductVo> vo = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        vo.setRecords(result.getRecords().stream().map(SeckillProductVo::from).collect(Collectors.toList()));
        return R.ok(vo);
    }

    /** 商品新增/编辑（vo.id 空=新增） */
    @PostMapping("/product")
    public R<Void> saveOrUpdateProduct(
            @Valid @RequestBody SeckillProductCreateVo vo,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        if (shopId == null || shopId > 0) {
            return R.fail("仅平台可管理秒杀商品");
        }
        seckillProductService.saveOrUpdateProduct(vo);
        return R.ok();
    }

    /** 商品逻辑删除 */
    @DeleteMapping("/product/{id}")
    public R<Void> deleteProduct(
            @PathVariable Long id,
            @RequestHeader(value = "X-Shop-Id", defaultValue = "0") Long shopId) {
        if (shopId == null || shopId > 0) {
            return R.fail("仅平台可管理秒杀商品");
        }
        seckillProductService.delete(id);
        return R.ok();
    }
}
