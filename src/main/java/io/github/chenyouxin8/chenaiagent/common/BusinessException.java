package io.github.chenyouxin8.chenaiagent.common;

/**
 * 业务异常（业务层主动抛出的错误）
 */
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
