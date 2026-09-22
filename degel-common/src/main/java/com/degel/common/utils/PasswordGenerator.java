package com.degel.common.utils;

import java.security.SecureRandom;

/**
 * 随机密码生成器：SecureRandom，剔除易混字符（0/O/1/l/I）。
 *
 * <p>用于重置密码、新店铺 owner 初始密码、空库初始化超管等场景，
 * 替代原固定口令 admin123（2026-09-20 安全扫描 H2 修复）。
 */
public final class PasswordGenerator {

    private static final String CHARS = "23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_LENGTH = 10;

    private PasswordGenerator() {
    }

    public static String generate() {
        return generate(DEFAULT_LENGTH);
    }

    public static String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
