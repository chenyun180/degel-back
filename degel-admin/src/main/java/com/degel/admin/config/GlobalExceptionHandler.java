package com.degel.admin.config;

import com.degel.common.core.R;
import com.degel.common.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** 并发办理同一流程任务（两位管理员同时审批）时后到者撞乐观锁 */
    @ExceptionHandler(FlowableOptimisticLockingException.class)
    public R<Void> handleFlowableOptimisticLocking(FlowableOptimisticLockingException e) {
        log.warn("Flowable optimistic locking: {}", e.getMessage());
        return R.fail("该记录已被他人处理，请刷新后重试");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return R.fail(400, message);
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("Unexpected error", e);
        return R.fail("系统内部错误");
    }
}
