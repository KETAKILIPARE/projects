package com.cloudresource.integration;

import com.cloudresource.domain.ResourceStatus;
import com.cloudresource.domain.ResourceType;
import com.cloudresource.dto.ResourceRequest;
import com.cloudresource.dto.ResourceResponse;
import com.cloudresource.service.ResourceService;
import com.cloudresource.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = com.cloudresource.controller.ResourceController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(com.cloudresource.config.SecurityConfig.class)
class ResourceSecurityIT {

    private static final String RESOURCE_NAME = "my-server";
    private static final String REGION        = "us-east-1";

    @Autowired private MockMvc       mockMvc;
    @Autowired private ObjectMapper  objectMapper;

    @MockBean private ResourceService    resourceService;
    @MockBean private JwtUtil            jwtUtil;
    @MockBean private UserDetailsService userDetailsService;

    @Test
    void createResource_returns403_whenCallerIsViewer() throws Exception {
        ResourceRequest request = new ResourceRequest(RESOURCE_NAME, ResourceType.EC2, REGION);

        mockMvc.perform(post("/api/resources")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void createResource_returns201_whenCallerIsOperator() throws Exception {
        ResourceRequest request = new ResourceRequest(RESOURCE_NAME, ResourceType.EC2, REGION);
        ResourceResponse resp   = Instancio.of(ResourceResponse.class)
                .set(field(ResourceResponse::status), ResourceStatus.PENDING)
                .create();
        when(resourceService.create(any(), any(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/resources")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void getAllResources_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/resources"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void getAllResources_returns200_whenCallerIsViewer() throws Exception {
        when(resourceService.findAll()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/resources"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void terminateResource_returns403_whenCallerIsViewer() throws Exception {
        mockMvc.perform(delete("/api/resources/{id}", UUID.randomUUID()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void terminateResource_returns200_whenCallerIsAdmin() throws Exception {
        UUID id   = UUID.randomUUID();
        ResourceResponse resp = Instancio.of(ResourceResponse.class)
                .set(field(ResourceResponse::status), ResourceStatus.TERMINATED)
                .create();
        when(resourceService.terminate(any(), any(), any())).thenReturn(resp);

        mockMvc.perform(delete("/api/resources/{id}", id).with(csrf()))
                .andExpect(status().isOk());
    }
}
