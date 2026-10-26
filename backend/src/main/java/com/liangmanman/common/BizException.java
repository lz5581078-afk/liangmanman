package com.liangmanman.common;

/** 业务异常：message 是给用户看的中文提示。 */
public class BizException extends RuntimeException {

    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int CONFLICT = 409;

    private final int code;

    public BizException(String message) {
        this(BAD_REQUEST, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BizException notFound(String what) {
        return new BizException(NOT_FOUND, what + "不存在");
    }

    public static BizException forbidden() {
        return new BizException(FORBIDDEN, "没有权限");
    }
}
