package io.github.chenyouxin8.chenaiagent.task;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RequestIdentityService {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String USER_HEADER = "X-User-Id";

    private final boolean trustIdentityHeaders;
    private final boolean requireIdentityHeaders;
    private final boolean oauth2Enabled;

    public RequestIdentityService(
            @Value("${chenmanus.security.trust-identity-headers:false}") boolean trustIdentityHeaders,
            @Value("${chenmanus.security.require-identity-headers:false}") boolean requireIdentityHeaders,
            @Value("${chenmanus.security.mode:legacy}") String securityMode
    ) {
        this.trustIdentityHeaders = trustIdentityHeaders;
        this.requireIdentityHeaders = requireIdentityHeaders;
        this.oauth2Enabled = "oauth2".equalsIgnoreCase(securityMode);
    }

    public String tenantId(HttpServletRequest request, String candidate) {
        if (oauth2Enabled) return requiredJwtClaim("tenant_id", "tenant");
        return resolve(request, TENANT_HEADER, candidate, "default");
    }

    public String userId(HttpServletRequest request, String candidate) {
        if (oauth2Enabled) return normalize(authenticated().getName(), "anonymous");
        return resolve(request, USER_HEADER, candidate, "anonymous");
    }

    public boolean isAdmin(HttpServletRequest request) {
        if (!oauth2Enabled) return false;
        return authenticated().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_TENANT_ADMIN".equalsIgnoreCase(authority.getAuthority())
                        || "ROLE_PLATFORM_ADMIN".equalsIgnoreCase(authority.getAuthority()));
    }

    // legacy 模式整体 permitAll（无任何鉴权），审批入口若仍要求管理员会永久卡死 WAITING_USER 任务
    public boolean canApprove(HttpServletRequest request) {
        return !oauth2Enabled || isAdmin(request);
    }

    public boolean isPlatformAdmin(HttpServletRequest request) {
        if (!oauth2Enabled) return false;
        return authenticated().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_PLATFORM_ADMIN".equalsIgnoreCase(authority.getAuthority()));
    }

    private String resolve(HttpServletRequest request, String header, String candidate, String fallback) {
        String headerValue = request == null ? null : request.getHeader(header);
        if (trustIdentityHeaders) {
            if (headerValue == null || headerValue.isBlank()) {
                if (requireIdentityHeaders) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少可信身份头：" + header);
                }
            } else {
                return normalize(headerValue, fallback);
            }
        }
        return normalize(candidate, fallback);
    }

    private String requiredJwtClaim(String primary, String secondary) {
        Authentication authentication = authenticated();
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OAuth2 身份不是有效 JWT");
        }
        Object value = jwt.getClaim(primary);
        if (value == null || String.valueOf(value).isBlank()) value = jwt.getClaim(secondary);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT 缺少租户标识 claim：" + primary);
        }
        return normalize(String.valueOf(value), null);
    }

    private Authentication authenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少有效登录身份");
        }
        return authentication;
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            if (fallback == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "身份标识不能为空");
            return fallback;
        }
        String normalized = value.trim();
        return normalized.substring(0, Math.min(128, normalized.length()));
    }
}
