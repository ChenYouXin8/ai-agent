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
 * Legacy API-key interceptor. Disabled when OAuth2 security mode is active;
 * JWT authentication is then handled by Spring Security.
 */
@Component
@Slf4j
public class SecurityInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${spring.security.api-key:}")
    private String configuredApiKey;

    @Value("${chenmanus.security.mode:legacy}")
    private String securityMode;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("oauth2".equalsIgnoreCase(securityMode)) return true;

        String path = request.getRequestURI();
        if (path.startsWith("/api/swagger") ||
            path.startsWith("/api/v3/api-docs") ||
            path.startsWith("/api/webjars") ||
            path.startsWith("/api/error") ||
            "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            log.debug("API Key 未配置，跳过鉴权");
            return true;
        }

        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "缺少 Authorization 头或格式错误，请使用：Authorization: Bearer <token>");
            return false;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (!configuredApiKey.equals(token)) {
            writeUnauthorized(response, "Token 无效");
            return false;
        }
        return true;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<?> error = ApiResponse.error(40100, message);
        response.getWriter().write(
            "{\"code\":40100,\"message\":\"" + error.getMessage() + "\",\"data\":null}"
        );
    }
}
