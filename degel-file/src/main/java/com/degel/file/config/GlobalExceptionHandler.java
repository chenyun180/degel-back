package com.degel.file.config;

import com.degel.common.core.R;
import com.degel.common.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理（2026-09-22 补齐，与 order/marketing/product 同款）。
 * 此前 degel-file 缺失 @RestControllerAdvice，H4 上传类型白名单抛出的
 * BusinessException 会变成裸 500 Internal Server Error，前端拿不到具体原因。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("Unexpected error", e);
        return R.fail("系统内部错误");
    }
}
