package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestIdentityServiceTest {

    @Test
    void trustedHeadersOverrideClientSuppliedIdentity() {
        RequestIdentityService service = new RequestIdentityService(true, true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "tenant-header");
        request.addHeader("X-User-Id", "user-header");

        assertEquals("tenant-header", service.tenantId(request, "tenant-body"));
        assertEquals("user-header", service.userId(request, "user-body"));
    }

    @Test
    void requiredTrustedIdentityFailsWhenHeaderIsMissing() {
        RequestIdentityService service = new RequestIdentityService(true, true);
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThrows(ResponseStatusException.class, () -> service.tenantId(request, "tenant-body"));
        assertThrows(ResponseStatusException.class, () -> service.userId(request, "user-body"));
    }

    @Test
    void developmentModeKeepsClientIdentityCompatibility() {
        RequestIdentityService service = new RequestIdentityService(false, false);
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertEquals("tenant-body", service.tenantId(request, "tenant-body"));
        assertEquals("user-body", service.userId(request, "user-body"));
    }
}
