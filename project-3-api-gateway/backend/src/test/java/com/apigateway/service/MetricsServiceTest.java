package com.apigateway.service;

import com.apigateway.dto.MetricsResponse;
import com.apigateway.repository.RequestLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    private static final long   TOTAL_REQUESTS        = 100L;
    private static final long   SUCCESS_REQUESTS      = 90L;
    private static final long   ERROR_REQUESTS        = 10L;
    private static final double AVG_LATENCY           = 45.5;
    private static final long   RATE_LIMITED_REQUESTS = 5L;
    private static final int    ERROR_THRESHOLD       = 400;
    private static final int    RATE_LIMIT_CODE       = 429;

    @Mock private RequestLogRepository requestLogRepository;

    @InjectMocks
    private MetricsService metricsService;

    @Test
    void getMetrics_shouldReturnCorrectTotals_whenAllDataPresent() {
        when(requestLogRepository.count()).thenReturn(TOTAL_REQUESTS);
        when(requestLogRepository.countByStatusCodeLessThan(ERROR_THRESHOLD)).thenReturn(SUCCESS_REQUESTS);
        when(requestLogRepository.countByStatusCodeGreaterThanEqual(ERROR_THRESHOLD)).thenReturn(ERROR_REQUESTS);
        when(requestLogRepository.averageLatencyMs()).thenReturn(AVG_LATENCY);
        when(requestLogRepository.countByStatusCode(RATE_LIMIT_CODE)).thenReturn(RATE_LIMITED_REQUESTS);

        MetricsResponse metrics = metricsService.getMetrics();

        assertThat(metrics.totalRequests())
                .as("total requests should match repository count")
                .isEqualTo(TOTAL_REQUESTS);
        assertThat(metrics.successRequests())
                .as("success requests should match count of sub-400 status codes")
                .isEqualTo(SUCCESS_REQUESTS);
        assertThat(metrics.errorRequests())
                .as("error requests should match count of 400+ status codes")
                .isEqualTo(ERROR_REQUESTS);
        assertThat(metrics.averageLatencyMs())
                .as("average latency should match repository value")
                .isEqualTo(AVG_LATENCY);
        assertThat(metrics.rateLimitedRequests())
                .as("rate limited requests should match count of 429 status codes")
                .isEqualTo(RATE_LIMITED_REQUESTS);
    }

    @Test
    void getMetrics_shouldReturnZeroLatency_whenRepositoryReturnsNull() {
        when(requestLogRepository.count()).thenReturn(0L);
        when(requestLogRepository.countByStatusCodeLessThan(ERROR_THRESHOLD)).thenReturn(0L);
        when(requestLogRepository.countByStatusCodeGreaterThanEqual(ERROR_THRESHOLD)).thenReturn(0L);
        when(requestLogRepository.averageLatencyMs()).thenReturn(null);
        when(requestLogRepository.countByStatusCode(RATE_LIMIT_CODE)).thenReturn(0L);

        MetricsResponse metrics = metricsService.getMetrics();

        assertThat(metrics.averageLatencyMs())
                .as("average latency should default to 0.0 when repository returns null")
                .isEqualTo(0.0);
    }
}
