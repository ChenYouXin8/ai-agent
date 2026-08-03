package io.github.chenyouxin8.chenaiagent.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 所有 Controller 层抛出的异常都会被这里捕获
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** 业务异常（主动抛出） */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<?> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /** AI 接口调用异常（最常见：API Key 失效/欠费/超时） */
    @ExceptionHandler(RestClientException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<?> handleApiException(RestClientException e) {
        log.error("AI 服务调用失败", e);
        return ApiResponse.serverError("AI 服务暂时不可用，请稍后再试");
    }

    /** 空指针异常（NPE，提前拦掉） */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleNPE(NullPointerException e) {
        log.error("空指针异常（代码bug）", e);
        return ApiResponse.serverError("系统内部错误，已记录");
    }

    /** 通用异常兜底（没料到的所有异常） */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleAll(Exception e) {
        log.error("未捕获的系统异常", e);
        return ApiResponse.serverError("系统繁忙，请稍后再试");
    }
}
