package com.degel.product.controller;

import com.degel.common.core.R;
import com.degel.product.entity.ProductSearchLog;
import com.degel.product.mapper.ProductSearchLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * C 端搜索埋点落库（degel-app 专用，经 Feign 直连；/inner/** 由 InnerTokenFilter 校验 X-Inner-Token）。
 * 埋点失败不影响搜索主链路：app 侧异步 best-effort 调用，本端点落库失败仅记日志不抛。
 */
@Slf4j
@RestController
@RequestMapping("/inner/spu")
@RequiredArgsConstructor
public class InnerSearchLogController {

    private final ProductSearchLogMapper searchLogMapper;

    /**
     * 记一条搜索事件（app 每次第一页搜索调一次；空结果也记，resultCount=0）
     */
    @PostMapping("/search-log")
    public R<Void> recordSearchLog(@RequestBody Map<String, Object> body) {
        try {
            Object keyword = body.get("keyword");
            if (keyword == null || String.valueOf(keyword).trim().isEmpty()) {
                return R.ok();
            }
            ProductSearchLog logEntry = new ProductSearchLog();
            logEntry.setKeyword(String.valueOf(keyword));
            Object userId = body.get("userId");
            // 匿名搜索 userId 为 null/0 均落 NULL（统计口径：登录占比按非空算）
            if (userId instanceof Number && ((Number) userId).longValue() > 0) {
                logEntry.setUserId(((Number) userId).longValue());
            }
            Object resultCount = body.get("resultCount");
            logEntry.setResultCount(resultCount instanceof Number ? ((Number) resultCount).intValue() : 0);
            searchLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("[search-log] 埋点落库失败 body={}", body, e);
        }
        return R.ok();
    }
}
