package com.apigateway.service;

import com.apigateway.domain.RouteConfig;
import com.apigateway.exception.RateLimitExceededException;
import com.apigateway.exception.RouteNotFoundException;
import com.apigateway.repository.RequestLogRepository;
import com.apigateway.repository.RouteConfigRepository;
import org.instancio.Instancio;
import org.instancio.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayServiceTest {

    private static final String PATH_PREFIX      = "/api/users";
    private static final String PATH_WITH_ID     = "/api/users/123";
    private static final String UNKNOWN_PATH     = "/unknown/resource";
    private static final String TARGET_URL       = "http://user-service:8081";
    private static final String CLIENT_IP        = "192.168.1.1";
    private static final int    RATE_LIMIT       = 10;

    @Mock private RouteConfigRepository routeConfigRepository;
    @Mock private RequestLogRepository requestLogRepository;
    @Mock private RateLimiterService rateLimiterService;

    @InjectMocks
    private GatewayService gatewayService;

    private Model<RouteConfig> routeModel;

    @BeforeEach
    void setUp() {
        routeModel = Instancio.of(RouteConfig.class)
                .set(field(RouteConfig::getPathPrefix), PATH_PREFIX)
                .set(field(RouteConfig::getTargetUrl), TARGET_URL)
                .set(field(RouteConfig::getRateLimit), RATE_LIMIT)
                .toModel();
    }

    @Test
    void resolveRoute_shouldReturnRouteConfig_whenRouteExistsAndUnderRateLimit() {
        RouteConfig route = Instancio.of(routeModel).create();
        when(routeConfigRepository.findByPathPrefixStartingWith(PATH_PREFIX)).thenReturn(Optional.of(route));
        when(rateLimiterService.isAllowed(anyString(), anyInt())).thenReturn(true);

        RouteConfig result = gatewayService.resolveRoute(PATH_WITH_ID, CLIENT_IP);

        assertThat(result.getTargetUrl())
                .as("should return the matched route's target URL")
                .isEqualTo(TARGET_URL);
    }

    @Test
    void resolveRoute_shouldThrowRouteNotFoundException_whenNoMatchingRoute() {
        when(routeConfigRepository.findByPathPrefixStartingWith(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gatewayService.resolveRoute(UNKNOWN_PATH, CLIENT_IP))
                .as("should throw RouteNotFoundException when no route matches the path")
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void resolveRoute_shouldThrowRateLimitExceededException_whenClientExceedsLimit() {
        RouteConfig route = Instancio.of(routeModel).create();
        when(routeConfigRepository.findByPathPrefixStartingWith(PATH_PREFIX)).thenReturn(Optional.of(route));
        when(rateLimiterService.isAllowed(anyString(), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> gatewayService.resolveRoute(PATH_WITH_ID, CLIENT_IP))
                .as("should throw RateLimitExceededException when client exceeds rate limit")
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void resolveRoute_shouldLogRequest_whenRouteResolvedSuccessfully() {
        RouteConfig route = Instancio.of(routeModel).create();
        when(routeConfigRepository.findByPathPrefixStartingWith(PATH_PREFIX)).thenReturn(Optional.of(route));
        when(rateLimiterService.isAllowed(anyString(), anyInt())).thenReturn(true);

        gatewayService.resolveRoute(PATH_WITH_ID, CLIENT_IP);

        verify(requestLogRepository).save(any());
    }

    @Test
    void resolveRoute_shouldLogRequest_whenRateLimitExceeded() {
        RouteConfig route = Instancio.of(routeModel).create();
        when(routeConfigRepository.findByPathPrefixStartingWith(PATH_PREFIX)).thenReturn(Optional.of(route));
        when(rateLimiterService.isAllowed(anyString(), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> gatewayService.resolveRoute(PATH_WITH_ID, CLIENT_IP))
                .isInstanceOf(RateLimitExceededException.class);

        verify(requestLogRepository).save(any());
    }
}
