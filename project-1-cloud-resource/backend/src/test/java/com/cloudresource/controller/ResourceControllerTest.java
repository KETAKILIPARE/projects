package com.cloudresource.controller;

import com.cloudresource.domain.ResourceStatus;
import com.cloudresource.domain.ResourceType;
import com.cloudresource.domain.UserRole;
import com.cloudresource.dto.ResourceRequest;
import com.cloudresource.dto.ResourceResponse;
import com.cloudresource.dto.ResourceStatusUpdateRequest;
import com.cloudresource.exception.ResourceNotFoundException;
import com.cloudresource.service.ResourceService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceControllerTest {

    private static final String OPERATOR_USERNAME = "operator1";
    private static final String ADMIN_USERNAME    = "admin1";
    private static final String RESOURCE_NAME     = "my-server";
    private static final String REGION            = "us-east-1";

    @Mock
    private ResourceService resourceService;

    @InjectMocks
    private ResourceController resourceController;

    private Model<ResourceResponse> responseModel;
    private UserDetails operatorUser;
    private UserDetails adminUser;

    @BeforeEach
    void setUp() {
        responseModel = Instancio.of(ResourceResponse.class)
                .set(field(ResourceResponse::name),          RESOURCE_NAME)
                .set(field(ResourceResponse::type),          ResourceType.EC2)
                .set(field(ResourceResponse::region),        REGION)
                .set(field(ResourceResponse::status),        ResourceStatus.RUNNING)
                .set(field(ResourceResponse::createdBy),     OPERATOR_USERNAME)
                .set(field(ResourceResponse::awsResourceId), "i-12345")
                .toModel();

        operatorUser = new User(OPERATOR_USERNAME, "",
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));

        adminUser = new User(ADMIN_USERNAME, "",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void create_returns201_whenRequestIsValid() {
        ResourceRequest request  = new ResourceRequest(RESOURCE_NAME, ResourceType.EC2, REGION);
        ResourceResponse created = Instancio.of(responseModel)
                .set(field(ResourceResponse::status), ResourceStatus.PENDING)
                .create();
        when(resourceService.create(any(), any(), any())).thenReturn(created);

        ResponseEntity<ResourceResponse> result = resourceController.create(request, operatorUser);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().status()).isEqualTo(ResourceStatus.PENDING);
    }

    @Test
    void create_returnsCreatedResource_withCorrectName() {
        ResourceRequest request  = new ResourceRequest(RESOURCE_NAME, ResourceType.EC2, REGION);
        ResourceResponse created = Instancio.of(responseModel).create();
        when(resourceService.create(any(), any(), any())).thenReturn(created);

        ResponseEntity<ResourceResponse> result = resourceController.create(request, operatorUser);

        assertThat(result.getBody().name()).isEqualTo(RESOURCE_NAME);
    }

    @Test
    void getById_returns200_whenResourceExists() {
        UUID id               = UUID.randomUUID();
        ResourceResponse resp = Instancio.of(responseModel).set(field(ResourceResponse::id), id).create();
        when(resourceService.findById(id)).thenReturn(resp);

        ResponseEntity<ResourceResponse> result = resourceController.getById(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().id()).isEqualTo(id);
    }

    @Test
    void getById_throwsResourceNotFound_whenDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(resourceService.findById(id)).thenThrow(new ResourceNotFoundException("Not found"));

        assertThatThrownBy(() -> resourceController.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAll_returns200WithList_whenResourcesExist() {
        ResourceResponse resp = Instancio.of(responseModel).create();
        when(resourceService.findAll()).thenReturn(List.of(resp));

        ResponseEntity<List<ResourceResponse>> result = resourceController.getAll();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    void getAll_returnsEmptyList_whenNoResourcesExist() {
        when(resourceService.findAll()).thenReturn(List.of());

        ResponseEntity<List<ResourceResponse>> result = resourceController.getAll();

        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void terminate_returns200WithTerminatedStatus_whenResourceIsRunning() {
        UUID id                  = UUID.randomUUID();
        ResourceResponse terminated = Instancio.of(responseModel)
                .set(field(ResourceResponse::id),     id)
                .set(field(ResourceResponse::status), ResourceStatus.TERMINATED)
                .create();
        when(resourceService.terminate(eq(id), any(), any())).thenReturn(terminated);

        ResponseEntity<ResourceResponse> result = resourceController.terminate(id, adminUser);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().status()).isEqualTo(ResourceStatus.TERMINATED);
    }

    @Test
    void updateStatus_returns200_whenStatusIsValid() {
        UUID id               = UUID.randomUUID();
        ResourceResponse resp = Instancio.of(responseModel)
                .set(field(ResourceResponse::status), ResourceStatus.STOPPED)
                .create();
        ResourceStatusUpdateRequest statusRequest = new ResourceStatusUpdateRequest(ResourceStatus.STOPPED);
        when(resourceService.updateStatus(eq(id), any(), any(), any())).thenReturn(resp);

        ResponseEntity<ResourceResponse> result = resourceController.updateStatus(id, statusRequest, operatorUser);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().status()).isEqualTo(ResourceStatus.STOPPED);
    }
}
