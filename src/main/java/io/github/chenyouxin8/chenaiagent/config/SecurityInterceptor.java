package io.github.chenyouxin8.chenaiagent.config;

import io.github.chenyouxin8.chenaiagent.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 接口鉴权拦截器
 *
 * 验证请求头中的 Bearer Token：
 * - 请求头格式：Authorization: Bearer <token>
 * - token 与配置文件中的 spring.security.api-key 一致则放行
 * - 未配置 api-key 时跳过验证（开发模式）
 * - 静态资源、错误路径、OPTIONS 预检请求跳过验证
 */
@Component
@Slf4j
public class SecurityInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${spring.security.api-key:}")
    private String configuredApiKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // 放行：静态资源、错误路径、OPTIONS 预检
        if (path.startsWith("/api/swagger") ||
            path.startsWith("/api/v3/api-docs") ||
            path.startsWith("/api/webjars") ||
            path.startsWith("/api/error") ||
            "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 未配置 api-key：开发模式，跳过验证
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            log.debug("API Key 未配置，跳过鉴权");
            return true;
        }

        // 提取 Bearer Token
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "缺少 Authorization 头或格式错误，请使用：Authorization: Bearer <token>");
            return false;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (!configuredApiKey.equals(token)) {
            log.warn("鉴权失败：Token 不匹配，来源 IP={}", request.getRemoteAddr());
            writeUnauthorized(response, "Token 无效");
            return false;
        }

        log.debug("鉴权通过：{}", request.getRemoteAddr());
        return true;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<?> error = ApiResponse.error(40100, message);
        response.getWriter().write(
            "{\"code\":40100,\"message\":\"" + message + "\",\"data\":null}"
        );
    }
}