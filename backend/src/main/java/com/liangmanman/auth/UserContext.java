package com.liangmanman.auth;

import com.liangmanman.common.BizException;

/** 当前请求的登录用户 ID（由 AuthInterceptor 写入） */
public final class UserContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private UserContext() {}

    static void set(Long userId) {
        CURRENT.set(userId);
    }

    static void clear() {
        CURRENT.remove();
    }

    /** 未登录时返回 null，用于“登录了就个性化，没登录也能看”的接口 */
    public static Long currentOrNull() {
        return CURRENT.get();
    }

    public static Long require() {
        Long id = CURRENT.get();
        if (id == null) {
            throw new BizException(BizException.UNAUTHORIZED, "请先登录");
        }
        return id;
    }
}
