package com.degel.common.utils;

/**
 * 日志脱敏工具：保留首尾各 4 字符，中间打码（长度不足整串打码）。
 * 用于 openid、资格 token 等需要跨日志追踪但不宜明文落盘的标识。
 */
public final class LogMask {

    private LogMask() {
    }

    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
