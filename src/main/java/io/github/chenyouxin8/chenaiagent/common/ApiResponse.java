package io.github.chenyouxin8.chenaiagent.common;

import java.time.LocalDateTime;

/**
 * 统一 API 响应格式
 * 所有接口返回值都是这个格式，方便前端处理
 */
public record ApiResponse<T>(
        int code,           // 业务状态码（0=成功，其他=失败）
        String message,     // 给用户看的提示信息
        T data,             // 实际数据
        LocalDateTime time  // 时间戳
) {
    /** 成功响应 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "操作成功", data, LocalDateTime.now());
    }

    /** 成功响应（无数据） */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "操作成功", null, LocalDateTime.now());
    }

    /** 失败响应 */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, LocalDateTime.now());
    }

    /** 快捷失败：参数错误 */
    public static <T> ApiResponse<T> badRequest(String message) {
        return error(40000, message);
    }

    /** 快捷失败：系统错误 */
    public static <T> ApiResponse<T> serverError(String message) {
        return error(50000, message);
    }
}
