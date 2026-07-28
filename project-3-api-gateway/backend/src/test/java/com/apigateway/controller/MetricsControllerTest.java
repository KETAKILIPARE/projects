package com.apigateway.controller;

import com.apigateway.dto.MetricsResponse;
import com.apigateway.service.MetricsService;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsControllerTest {

    @Mock private MetricsService metricsService;

    @InjectMocks
    private MetricsController metricsController;

    @Test
    void getMetrics_shouldReturn200_withMetricsFromService() {
        MetricsResponse expected = Instancio.create(MetricsResponse.class);
        when(metricsService.getMetrics()).thenReturn(expected);

        ResponseEntity<MetricsResponse> result = metricsController.getMetrics();

        assertThat(result.getStatusCode())
                .as("should return 200 OK for metrics endpoint")
                .isEqualTo(HttpStatus.OK);
        assertThat(result.getBody())
                .as("response body should be the metrics returned by the service")
                .isEqualTo(expected);
    }
}
