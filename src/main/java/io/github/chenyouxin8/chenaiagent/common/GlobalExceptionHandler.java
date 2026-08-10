package io.github.chenyouxin8.chenaiagent.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 全局异常处理器
 *
 * 所有 Controller 层抛出的异常都会被这里捕获，统一返回 ApiResponse 格式。
 * 注意：SSE 请求的异常由 AiController 内部 try-catch 处理，不走此 Handler。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 业务异常（主动抛出的业务错误）
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<?> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /**
     * AI 接口调用异常（API Key 失效/欠费/超时，最常见）
     */
    @ExceptionHandler(RestClientException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<?> handleApiException(RestClientException e) {
        log.error("AI 服务调用失败", e);
        return ApiResponse.serverError("AI 服务暂时不可用，请稍后再试");
    }

    /**
     * 空指针异常（NPE，提前兜底）
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleNPE(NullPointerException e) {
        log.error("空指针异常（代码bug）", e);
        return ApiResponse.serverError("系统内部错误，已记录");
    }

    /**
     * 参数校验异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数错误: {}", e.getMessage());
        return ApiResponse.badRequest(e.getMessage());
    }

    /**
     * Agent 状态异常（重复调用等）
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<?> handleIllegalState(IllegalStateException e) {
        log.warn("Agent 状态异常: {}", e.getMessage());
        return ApiResponse.error(40900, "操作冲突：" + e.getMessage());
    }

    /**
     * 通用异常兜底（所有未捕获的异常）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleAll(Exception e) {
        log.error("未捕获的系统异常", e);
        return ApiResponse.serverError("系统繁忙，请稍后再试");
    }
}