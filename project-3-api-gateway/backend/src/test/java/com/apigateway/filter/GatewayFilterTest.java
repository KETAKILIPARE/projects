package com.apigateway.filter;

import com.apigateway.domain.RouteConfig;
import com.apigateway.exception.RateLimitExceededException;
import com.apigateway.exception.RouteNotFoundException;
import com.apigateway.repository.RequestLogRepository;
import com.apigateway.service.GatewayService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.instancio.Instancio;
import org.instancio.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayFilterTest {

    private static final String JWT_SECRET       = "test-secret-key-minimum-32-characters-long!!";
    private static final String CLIENT_IP        = "127.0.0.1";
    private static final String TARGET_URL       = "http://backend-service:9000";
    private static final String PROXIED_PATH     = "/api/orders/1";
    private static final String METRICS_PATH     = "/api/metrics";
    private static final String ROUTES_PATH      = "/api/routes";
    private static final String GET_METHOD       = "GET";
    private static final int    STATUS_200       = 200;
    private static final int    STATUS_401       = 401;
    private static final int    STATUS_404       = 404;
    private static final int    STATUS_429       = 429;
    private static final int    STATUS_502       = 502;

    @Mock private GatewayService gatewayService;
    @Mock private RequestLogRepository requestLogRepository;
    @Mock private RestTemplate restTemplate;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private GatewayFilter gatewayFilter;

    private StringWriter responseBody;
    private Model<RouteConfig> noAuthRouteModel;
    private Model<RouteConfig> authRouteModel;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(gatewayFilter, "jwtSecret", JWT_SECRET);
        responseBody = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        lenient().when(request.getRemoteAddr()).thenReturn(CLIENT_IP);
        lenient().when(request.getMethod()).thenReturn(GET_METHOD);
        lenient().when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

        noAuthRouteModel = Instancio.of(RouteConfig.class)
                .set(field(RouteConfig::getTargetUrl), TARGET_URL)
                .set(field(RouteConfig::isRequiresAuth), false)
                .toModel();

        authRouteModel = Instancio.of(RouteConfig.class)
                .set(field(RouteConfig::getTargetUrl), TARGET_URL)
                .set(field(RouteConfig::isRequiresAuth), true)
                .toModel();
    }

    @Test
    void doFilter_shouldPassThrough_whenPathIsMetricsEndpoint() throws Exception {
        when(request.getRequestURI()).thenReturn(METRICS_PATH);

        gatewayFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(gatewayService, never()).resolveRoute(anyString(), anyString());
    }

    @Test
    void doFilter_shouldPassThrough_whenPathIsRoutesEndpoint() throws Exception {
        when(request.getRequestURI()).thenReturn(ROUTES_PATH);

        gatewayFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(gatewayService, never()).resolveRoute(anyString(), anyString());
    }

    @Test
    void doFilter_shouldReturn404_whenRouteNotFound() throws Exception {
        when(request.getRequestURI()).thenReturn(PROXIED_PATH);
        when(gatewayService.resolveRoute(PROXIED_PATH, CLIENT_IP))
                .thenThrow(new RouteNotFoundException("No route found"));

        gatewayFilter.doFilter(request, response, filterChain);

        verify(response).setStatus(STATUS_404);
        assertThat(responseBody.toString())
                .as("response body should contain error message for unknown route")
                .contains("No route found");
    }

    @Test
    void doFilter_shouldReturn429_whenRateLimitExceeded() throws Exception {
        when(request.getRequestURI()).thenReturn(PROXIED_PATH);
        when(gatewayService.resolveRoute(PROXIED_PATH, CLIENT_IP))
                .thenThrow(new RateLimitExceededException("Rate limit exceeded"));

        gatewayFilter.doFilter(request, response, filterChain);

        verify(response).setStatus(STATUS_429);
        assertThat(responseBody.toString())
                .as("response body should contain rate limit error message")
                .contains("Rate limit exceeded");
    }

    @Test
    void doFilter_shouldReturn401_whenRouteRequiresAuthAndNoAuthorizationHeader() throws Exception {
        when(request.getRequestURI()).thenReturn(PROXIED_PATH);
        when(gatewayService.resolveRoute(PROXIED_PATH, CLIENT_IP))
                .thenReturn(Instancio.of(authRouteModel).create());
        when(request.getHeader("Authorization")).thenReturn(null);

        gatewayFilter.doFilter(request, response, filterChain);

        verify(response).setStatus(STATUS_401);
        assertThat(responseBody.toString())
                .as("response body should indicate unauthorized when no auth header present")
                .contains("Unauthorized");
    }

    @Test
    void doFilter_shouldReturn401_whenRouteRequiresAuthAndJwtIsInvalid() throws Exception {
        when(request.getRequestURI()).thenReturn(PROXIED_PATH);
        when(gatewayService.resolveRoute(PROXIED_PATH, CLIENT_IP))
                .thenReturn(Instancio.of(authRouteModel).create());
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid.jwt.token");

        gatewayFilter.doFilter(request, response, filterChain);

        verify(response).setStatus(STATUS_401);
        assertThat(responseBody.toString())
                .as("response body should indicate unauthorized when JWT is invalid")
                .contains("Unauthorized");
    }

    @Test
    void doFilter_shouldForwardRequest_whenRouteRequiresAuthAndJwtIsValid() throws Exception {
        String validJwt = Jwts.builder()
                .setSubject("test-user")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes()))
                .compact();

        when(request.getRequestURI()).thenReturn(PROXIED_PATH);
        when(gatewayService.resolveRoute(PROXIED_PATH, CLIENT_IP))
                .thenReturn(Instancio.of(authRouteModel).create());
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validJwt);
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));

        gatewayFilter.doFilter(request, response, filterChain);

        verify(response).setStatus(STATUS_200);
    }

    @Test
    void doFilter_shouldForwardRequest_whenRouteDoesNotRequireAuth() throws Exception {
        when(request.getRequestURI()).thenReturn(PROXIED_PATH);
        when(gatewayService.resolveRoute(PROXIED_PATH, CLIENT_IP))
                .thenReturn(Instancio.of(noAuthRouteModel).create());
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));

        gatewayFilter.doFilter(request, response, filterChain);

        verify(response).setStatus(STATUS_200);
        verify(requestLogRepository, org.mockito.Mockito.atLeastOnce()).save(any());
    }

    @Test
    void doFilter_shouldReturn502_whenDownstreamThrowsException() throws Exception {
        when(request.getRequestURI()).thenReturn(PROXIED_PATH);
        when(gatewayService.resolveRoute(PROXIED_PATH, CLIENT_IP))
                .thenReturn(Instancio.of(noAuthRouteModel).create());
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        gatewayFilter.doFilter(request, response, filterChain);

        verify(response).setStatus(STATUS_502);
        assertThat(responseBody.toString())
                .as("response body should indicate bad gateway when downstream is unreachable")
                .contains("Bad gateway");
    }
}
