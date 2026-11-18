package com.qsx.appoint.localenv;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LocalEnvironmentHttpTest {
    private final LocalEnvironmentLifecycle lifecycle = mock(LocalEnvironmentLifecycle.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void reportsActualEnvironmentAndWaitsForReadiness() {
        when(lifecycle.name()).thenReturn("demo");
        var availability = mock(ApplicationAvailability.class);
        var env = new MockEnvironment().withProperty("APPOINT_LOCAL_LAUNCH_ID", "launch-1");
        var controller = new LocalEnvironmentStatusController(lifecycle, availability, env);

        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
        assertThat(controller.status().getStatusCode().value()).isEqualTo(503);
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        var response = controller.status();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isEqualTo(new LocalEnvironmentStatusController.Status("demo", true, "launch-1"));
    }

    @Test
    void rejectsOldPageBeforeBusinessIncludingLogin() throws Exception {
        when(lifecycle.name()).thenReturn("test");
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader(LocalEnvironmentRequestFilter.HEADER, "demo");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        new LocalEnvironmentRequestFilter(lifecycle, mapper).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(mapper.readTree(response.getContentAsByteArray()).path("code").asInt()).isEqualTo(1003);
        verifyNoInteractions(chain);
    }

    @Test
    void permitsMatchingEnvironmentAndClientsWithoutEnvironmentHeader() throws Exception {
        when(lifecycle.name()).thenReturn("test");
        var filter = new LocalEnvironmentRequestFilter(lifecycle, mapper);
        var chain = mock(FilterChain.class);
        for (String profile : new String[]{null, "test"}) {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            if (profile != null) request.addHeader(LocalEnvironmentRequestFilter.HEADER, profile);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            verify(chain).doFilter(request, response);
        }
    }
}
