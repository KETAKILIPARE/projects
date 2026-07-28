package com.apigateway;

import com.apigateway.dto.RouteRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Date;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ApiGatewayIntegrationTest {

    private static final String JWT_SECRET          = "test-secret-key-minimum-32-characters-long!!";
    private static final String ROUTE_PATH_PREFIX   = "/api/orders";
    private static final String PROXIED_PATH        = "/api/orders/1";
    private static final String AUTH_ROUTE_PREFIX   = "/api/secure";
    private static final String AUTH_PROXIED_PATH   = "/api/secure/data";
    private static final String DOWNSTREAM_RESPONSE = "{\"id\":1,\"status\":\"ok\"}";
    private static final int    WIREMOCK_PORT       = 9090;
    private static final int    RATE_LIMIT_LOW      = 2;
    private static final int    RATE_LIMIT_HIGH     = 100;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().port(WIREMOCK_PORT));
        wireMockServer.start();
        WireMock.configureFor("localhost", WIREMOCK_PORT);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private String targetUrl() {
        return "http://localhost:" + WIREMOCK_PORT;
    }

    private String validJwt() {
        return Jwts.builder()
                .setSubject("test-user")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes()))
                .compact();
    }

    private void registerRoute(String pathPrefix, int rateLimit, boolean requiresAuth) throws Exception {
        RouteRequest request = new RouteRequest(pathPrefix, targetUrl(), rateLimit, requiresAuth);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private void stubDownstream(String path) {
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(path))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(DOWNSTREAM_RESPONSE)));
    }

    @Test
    void createRoute_shouldReturn201_andAppearInRouteList() throws Exception {
        registerRoute(ROUTE_PATH_PREFIX, RATE_LIMIT_HIGH, false);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].pathPrefix").value(ROUTE_PATH_PREFIX));
    }

    @Test
    void deleteRoute_shouldReturn204_andRouteNoLongerListed() throws Exception {
        registerRoute(ROUTE_PATH_PREFIX, RATE_LIMIT_HIGH, false);

        MvcResult listResult = mockMvc.perform(MockMvcRequestBuilders.get("/api/routes")).andReturn();
        String routeId = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .get(0).get("id").asText();

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/routes/" + routeId))
                .andExpect(status().isNoContent());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void proxyRequest_shouldForwardToDownstream_whenRouteExists() throws Exception {
        stubDownstream(PROXIED_PATH);
        registerRoute(ROUTE_PATH_PREFIX, RATE_LIMIT_HIGH, false);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get(PROXIED_PATH)).andReturn();

        assertThat(result.getResponse().getStatus())
                .as("gateway should forward request and return downstream status")
                .isEqualTo(200);
        assertThat(result.getResponse().getContentAsString())
                .as("gateway should return downstream response body")
                .isEqualTo(DOWNSTREAM_RESPONSE);
    }

    @Test
    void proxyRequest_shouldReturn404_whenNoRouteRegistered() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(PROXIED_PATH))
                .andExpect(status().isNotFound());
    }

    @Test
    void proxyRequest_shouldReturn429_whenRateLimitExceeded() throws Exception {
        stubDownstream(PROXIED_PATH);
        registerRoute(ROUTE_PATH_PREFIX, RATE_LIMIT_LOW, false);

        for (int i = 0; i < RATE_LIMIT_LOW; i++) {
            mockMvc.perform(MockMvcRequestBuilders.get(PROXIED_PATH)).andReturn();
        }

        mockMvc.perform(MockMvcRequestBuilders.get(PROXIED_PATH))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void proxyRequest_shouldReturn401_whenRouteRequiresAuthAndNoToken() throws Exception {
        registerRoute(AUTH_ROUTE_PREFIX, RATE_LIMIT_HIGH, true);

        mockMvc.perform(MockMvcRequestBuilders.get(AUTH_PROXIED_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void proxyRequest_shouldReturn401_whenRouteRequiresAuthAndTokenIsInvalid() throws Exception {
        registerRoute(AUTH_ROUTE_PREFIX, RATE_LIMIT_HIGH, true);

        mockMvc.perform(MockMvcRequestBuilders.get(AUTH_PROXIED_PATH)
                .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void proxyRequest_shouldForward_whenRouteRequiresAuthAndTokenIsValid() throws Exception {
        stubDownstream(AUTH_PROXIED_PATH);
        registerRoute(AUTH_ROUTE_PREFIX, RATE_LIMIT_HIGH, true);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get(AUTH_PROXIED_PATH)
                .header("Authorization", "Bearer " + validJwt()))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("gateway should forward request when valid JWT is provided")
                .isEqualTo(200);
    }

    @Test
    void getMetrics_shouldReflectRequestCounts_afterProxying() throws Exception {
        stubDownstream(PROXIED_PATH);
        registerRoute(ROUTE_PATH_PREFIX, RATE_LIMIT_HIGH, false);
        mockMvc.perform(MockMvcRequestBuilders.get(PROXIED_PATH)).andReturn();

        mockMvc.perform(MockMvcRequestBuilders.get("/api/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(greaterThan(0)));
    }
}
