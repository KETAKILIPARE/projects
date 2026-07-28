package com.apigateway.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterServiceTest {

    private static final String CLIENT_A        = "client-a";
    private static final String CLIENT_B        = "client-b";
    private static final int    LIMIT_3         = 3;
    private static final int    LIMIT_1         = 1;
    private static final long   SHORT_WINDOW_MS = 100L;

    private final RateLimiterService rateLimiterService = new RateLimiterService();

    @Test
    void isAllowed_shouldReturnTrue_whenFirstRequestUnderLimit() {
        assertThat(rateLimiterService.isAllowed(CLIENT_A, LIMIT_3))
                .as("first request should be allowed when under the rate limit")
                .isTrue();
    }

    @Test
    void isAllowed_shouldReturnFalse_whenLimitExceeded() {
        for (int i = 0; i < LIMIT_3; i++) {
            rateLimiterService.isAllowed(CLIENT_A, LIMIT_3);
        }

        assertThat(rateLimiterService.isAllowed(CLIENT_A, LIMIT_3))
                .as("request beyond the limit should be denied")
                .isFalse();
    }

    @Test
    void isAllowed_shouldReturnTrue_atExactLimit() {
        for (int i = 0; i < LIMIT_3 - 1; i++) {
            rateLimiterService.isAllowed(CLIENT_A, LIMIT_3);
        }

        assertThat(rateLimiterService.isAllowed(CLIENT_A, LIMIT_3))
                .as("request exactly at the limit boundary should still be allowed")
                .isTrue();
    }

    @Test
    void isAllowed_shouldTrackClientsIndependently() {
        for (int i = 0; i < LIMIT_3; i++) {
            rateLimiterService.isAllowed(CLIENT_A, LIMIT_3);
        }

        assertThat(rateLimiterService.isAllowed(CLIENT_B, LIMIT_3))
                .as("a different client should not be affected by another client's usage")
                .isTrue();
    }

    @Test
    void isAllowed_shouldAllowRequests_afterWindowExpires() throws InterruptedException {
        RateLimiterService shortWindowService = new RateLimiterService(SHORT_WINDOW_MS);
        shortWindowService.isAllowed(CLIENT_A, LIMIT_1);
        shortWindowService.isAllowed(CLIENT_A, LIMIT_1);

        Thread.sleep(SHORT_WINDOW_MS + 50L);

        assertThat(shortWindowService.isAllowed(CLIENT_A, LIMIT_1))
                .as("request should be allowed after the rate limit window has expired")
                .isTrue();
    }
}
