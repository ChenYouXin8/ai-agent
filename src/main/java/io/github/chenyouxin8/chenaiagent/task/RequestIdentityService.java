package io.github.chenyouxin8.chenaiagent.task;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RequestIdentityService {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String USER_HEADER = "X-User-Id";

    private final boolean trustIdentityHeaders;
    private final boolean requireIdentityHeaders;

    public RequestIdentityService(
            @Value("${chenmanus.security.trust-identity-headers:false}") boolean trustIdentityHeaders,
            @Value("${chenmanus.security.require-identity-headers:false}") boolean requireIdentityHeaders
    ) {
        this.trustIdentityHeaders = trustIdentityHeaders;
        this.requireIdentityHeaders = requireIdentityHeaders;
    }

    public String tenantId(HttpServletRequest request, String candidate) {
        return resolve(request, TENANT_HEADER, candidate, "default");
    }

    public String userId(HttpServletRequest request, String candidate) {
        return resolve(request, USER_HEADER, candidate, "anonymous");
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

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim();
        return normalized.substring(0, Math.min(128, normalized.length()));
    }
}
