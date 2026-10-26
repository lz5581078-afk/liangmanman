package com.liangmanman.admin;

import com.liangmanman.common.BizException;
import com.liangmanman.entity.Admin;

/** 当前请求的管理员（由 AdminInterceptor 写入） */
public final class AdminContext {

    private static final ThreadLocal<Admin> CURRENT = new ThreadLocal<>();

    private AdminContext() {}

    static void set(Admin admin) {
        CURRENT.set(admin);
    }

    static void clear() {
        CURRENT.remove();
    }

    public static Admin require() {
        Admin a = CURRENT.get();
        if (a == null) throw new BizException(BizException.UNAUTHORIZED, "请先登录");
        return a;
    }

    public static Admin requireSuper() {
        Admin a = require();
        if (!Admin.ADMIN.equals(a.getRole())) throw BizException.forbidden();
        return a;
    }
}
