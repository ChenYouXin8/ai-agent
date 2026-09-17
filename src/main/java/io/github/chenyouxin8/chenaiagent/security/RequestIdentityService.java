package io.github.chenyouxin8.chenaiagent.security;

import io.github.chenyouxin8.chenaiagent.task.TaskScopeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Service
public class RequestIdentityService {

    private final TaskScopeService scopeService;
    private final String securityMode;
    private final boolean requireIdentityHeaders;

    public RequestIdentityService(
            TaskScopeService scopeService,
            @Value("${chenmanus.security.mode:legacy}") String securityMode,
            @Value("${chenmanus.security.require-identity:false}") boolean requireIdentityHeaders
    ) {
        this.scopeService = scopeService;
        this.securityMode = securityMode;
        this.requireIdentityHeaders = requireIdentityHeaders;
    }

    public TaskIdentity resolve(String requestedTenantId, String requestedUserId) {
        if ("oauth2".equalsIgnoreCase(securityMode)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!(authentication instanceof JwtAuthenticationToken token)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OAuth2 身份未建立");
            }
            Jwt jwt = token.getToken();
            String tenantId = firstNonBlank(
                    jwt.getClaimAsString("tenant_id"),
                    jwt.getClaimAsString("tenantId"),
                    jwt.getClaimAsString("org_id"));
            String userId = firstNonBlank(jwt.getSubject(), jwt.getClaimAsString("user_id"));
            if (tenantId == null || userId == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT 缺少 tenant_id 或 sub/user_id");
            }
            return new TaskIdentity(
                    scopeService.normalize(tenantId, "default"),
                    scopeService.normalize(userId, "anonymous"),
                    authorities(authentication),
                    true
            );
        }

        if (requireIdentityHeaders && (requestedTenantId == null || requestedUserId == null)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少租户或用户身份");
        }
        return new TaskIdentity(
                scopeService.normalize(requestedTenantId, "default"),
                scopeService.normalize(requestedUserId, "anonymous"),
                Set.of("USER"),
                false
        );
    }

    private Set<String> authorities(Authentication authentication) {
        Set<String> roles = new LinkedHashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (value != null && !value.isBlank()) {
                roles.add(value.startsWith("ROLE_") ? value.substring(5) : value);
            }
        }
        if (roles.isEmpty()) roles.add("USER");
        return Set.copyOf(roles);
    }

    private String firstNonBlank(String... values) {
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
