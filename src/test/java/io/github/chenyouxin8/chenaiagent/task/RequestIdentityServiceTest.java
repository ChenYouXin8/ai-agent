package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestIdentityServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void trustedHeadersOverrideClientSuppliedIdentity() {
        RequestIdentityService service = new RequestIdentityService(true, true, "legacy");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "tenant-header");
        request.addHeader("X-User-Id", "user-header");

        assertEquals("tenant-header", service.tenantId(request, "tenant-body"));
        assertEquals("user-header", service.userId(request, "user-body"));
        assertFalse(service.isAdmin(request));
    }

    @Test
    void requiredTrustedIdentityFailsWhenHeaderIsMissing() {
        RequestIdentityService service = new RequestIdentityService(true, true, "legacy");
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThrows(ResponseStatusException.class, () -> service.tenantId(request, "tenant-body"));
        assertThrows(ResponseStatusException.class, () -> service.userId(request, "user-body"));
    }

    @Test
    void developmentModeKeepsClientIdentityCompatibility() {
        RequestIdentityService service = new RequestIdentityService(false, false, "legacy");
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertEquals("tenant-body", service.tenantId(request, "tenant-body"));
        assertEquals("user-body", service.userId(request, "user-body"));
    }

    @Test
    void oauth2ModeUsesJwtTenantAndSubject() {
        RequestIdentityService service = new RequestIdentityService(false, false, "oauth2");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-from-jwt")
                .claim("tenant_id", "tenant-from-jwt")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        assertEquals("tenant-from-jwt", service.tenantId(new MockHttpServletRequest(), "tenant-body"));
        assertEquals("user-from-jwt", service.userId(new MockHttpServletRequest(), "user-body"));
    }

    @Test
    void oauth2AdminRoleIsExposed() {
        RequestIdentityService service = new RequestIdentityService(false, false, "oauth2");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("admin-user")
                .claim("tenant_id", "tenant-a")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))));

        assertTrue(service.isAdmin(new MockHttpServletRequest()));
    }

    @Test
    void legacyModeAllowsApproval() {
        RequestIdentityService service = new RequestIdentityService(false, false, "legacy");
        assertTrue(service.canApprove(new MockHttpServletRequest()));
    }

    @Test
    void oauth2ModeBlocksApprovalWithoutAdminRole() {
        RequestIdentityService service = new RequestIdentityService(false, false, "oauth2");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("normal-user")
                .claim("tenant_id", "tenant-a")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        assertFalse(service.canApprove(new MockHttpServletRequest()));
    }

    @Test
    void oauth2ModeAllowsApprovalForTenantAdmin() {
        RequestIdentityService service = new RequestIdentityService(false, false, "oauth2");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("admin-user")
                .claim("tenant_id", "tenant-a")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))));

        assertTrue(service.canApprove(new MockHttpServletRequest()));
    }

    @Test
    void oauth2ModeRejectsJwtWithoutTenantClaim() {
        RequestIdentityService service = new RequestIdentityService(false, false, "oauth2");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-from-jwt")
                .claims(claims -> claims.putAll(Map.of("scope", "tasks.read")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        assertThrows(ResponseStatusException.class,
                () -> service.tenantId(new MockHttpServletRequest(), "tenant-body"));
    }
}
