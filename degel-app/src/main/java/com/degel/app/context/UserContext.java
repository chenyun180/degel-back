package com.degel.app.context;

/**
 * C端用户上下文
 * 基于 ThreadLocal 封装 mallUserId，防止线程复用带来的脏数据
 * 使用方式：
 *   set:   UserContext.setUserId(id)
 *   get:   UserContext.getUserId()
 *   clean: UserContext.clear()  // 必须在 finally 中调用
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    private UserContext() {
        // 工具类，禁止实例化
    }

    /**
     * 设置当前登录用户ID
     */
    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    /**
     * 获取当前登录用户ID，未设置时返回 null
     */
    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    /**
     * 清除用户上下文（必须在请求结束的 finally 块中调用，防止内存泄漏）
     */
    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}
