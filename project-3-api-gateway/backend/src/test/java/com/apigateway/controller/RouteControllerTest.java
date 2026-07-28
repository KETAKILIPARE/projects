package com.apigateway.controller;

import com.apigateway.domain.RouteConfig;
import com.apigateway.dto.RouteRequest;
import com.apigateway.repository.RouteConfigRepository;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteControllerTest {

    private static final String PATH_PREFIX  = "/api/orders";
    private static final String TARGET_URL   = "http://order-service:8084";
    private static final int    RATE_LIMIT   = 50;
    private static final boolean REQUIRES_AUTH = true;

    @Mock private RouteConfigRepository routeConfigRepository;

    @InjectMocks
    private RouteController routeController;

    private Model<RouteConfig> routeModel;

    @BeforeEach
    void setUp() {
        routeModel = Instancio.of(RouteConfig.class)
                .set(field(RouteConfig::getPathPrefix), PATH_PREFIX)
                .set(field(RouteConfig::getTargetUrl), TARGET_URL)
                .set(field(RouteConfig::getRateLimit), RATE_LIMIT)
                .set(field(RouteConfig::isRequiresAuth), REQUIRES_AUTH)
                .toModel();
    }

    @Test
    void create_shouldReturn201_whenRouteIsCreated() {
        RouteRequest request = new RouteRequest(PATH_PREFIX, TARGET_URL, RATE_LIMIT, REQUIRES_AUTH);
        RouteConfig saved = Instancio.of(routeModel).create();
        when(routeConfigRepository.save(any(RouteConfig.class))).thenReturn(saved);

        ResponseEntity<RouteConfig> result = routeController.create(request);

        assertThat(result.getStatusCode())
                .as("should return 201 CREATED when a route is successfully registered")
                .isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getPathPrefix())
                .as("response body should contain the registered path prefix")
                .isEqualTo(PATH_PREFIX);
    }

    @Test
    void getAll_shouldReturn200_withListOfRoutes() {
        List<RouteConfig> routes = Instancio.ofList(RouteConfig.class).size(3).create();
        when(routeConfigRepository.findAll()).thenReturn(routes);

        ResponseEntity<List<RouteConfig>> result = routeController.getAll();

        assertThat(result.getStatusCode())
                .as("should return 200 OK when fetching all routes")
                .isEqualTo(HttpStatus.OK);
        assertThat(result.getBody())
                .as("should return all registered routes")
                .hasSize(3);
    }

    @Test
    void getAll_shouldReturn200_withEmptyList_whenNoRoutesRegistered() {
        when(routeConfigRepository.findAll()).thenReturn(List.of());

        ResponseEntity<List<RouteConfig>> result = routeController.getAll();

        assertThat(result.getStatusCode())
                .as("should return 200 OK even when no routes are registered")
                .isEqualTo(HttpStatus.OK);
        assertThat(result.getBody())
                .as("should return empty list when no routes exist")
                .isEmpty();
    }

    @Test
    void delete_shouldReturn204_andDelegateToRepository() {
        UUID routeId = UUID.randomUUID();

        ResponseEntity<Void> result = routeController.delete(routeId);

        assertThat(result.getStatusCode())
                .as("should return 204 NO CONTENT when a route is deleted")
                .isEqualTo(HttpStatus.NO_CONTENT);
        verify(routeConfigRepository).deleteById(routeId);
    }
}
