package com.degel.app.exception;

import lombok.Getter;

/**
 * C端业务异常
 * 抛出此异常将由 GlobalExceptionHandler 捕获并返回统一格式 R<T>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final String message;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    // ===== 常用静态工厂方法 =====

    public static BusinessException of(int code, String message) {
        return new BusinessException(code, message);
    }

    /** 40001 微信授权失败 */
    public static BusinessException wxAuthFail() {
        return new BusinessException(40001, "微信授权失败，code 无效或已过期");
    }

    /** 40002 账号已封禁 */
    public static BusinessException accountBanned() {
        return new BusinessException(40002, "账号已封禁");
    }

    /** 40003 用户名或密码错误 */
    public static BusinessException loginFail() {
        return new BusinessException(40003, "用户名或密码错误");
    }

    /** 40400 地址不存在 */
    public static BusinessException addressNotFound() {
        return new BusinessException(40400, "地址不存在");
    }

    /** 40300 无权操作他人地址 */
    public static BusinessException addressForbidden() {
        return new BusinessException(40300, "无权操作他人地址");
    }
}
