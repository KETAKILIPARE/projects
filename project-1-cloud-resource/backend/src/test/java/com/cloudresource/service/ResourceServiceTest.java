package com.cloudresource.service;

import com.cloudresource.domain.*;
import com.cloudresource.dto.ResourceRequest;
import com.cloudresource.dto.ResourceResponse;
import com.cloudresource.exception.AccessDeniedException;
import com.cloudresource.exception.InvalidStateTransitionException;
import com.cloudresource.exception.ResourceNotFoundException;
import com.cloudresource.repository.AuditLogRepository;
import com.cloudresource.repository.ResourceRepository;
import org.instancio.Instancio;
import org.instancio.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    private static final String OPERATOR_USERNAME = "operator1";
    private static final String ADMIN_USERNAME    = "admin1";
    private static final String VIEWER_USERNAME   = "viewer1";
    private static final String RESOURCE_NAME     = "my-server";
    private static final String REGION            = "us-east-1";

    @Mock private ResourceRepository      resourceRepository;
    @Mock private AuditLogRepository      auditLogRepository;
    @Mock private AwsProvisioningService  awsProvisioningService;

    @InjectMocks
    private ResourceService resourceService;

    private Model<Resource> ec2ResourceModel;
    private ResourceRequest ec2Request;

    @BeforeEach
    void setUp() {
        ec2Request = new ResourceRequest(RESOURCE_NAME, ResourceType.EC2, REGION);

        ec2ResourceModel = Instancio.of(Resource.class)
                .set(field(Resource::getName),      RESOURCE_NAME)
                .set(field(Resource::getType),      ResourceType.EC2)
                .set(field(Resource::getRegion),    REGION)
                .set(field(Resource::getCreatedBy), OPERATOR_USERNAME)
                .set(field(Resource::getStatus),    ResourceStatus.PENDING)
                .toModel();
    }

    @Test
    void create_returnsRunningStatus_whenAwsProvisioningSucceeds() {
        Resource resource = Instancio.of(ec2ResourceModel).create();
        when(resourceRepository.save(any(Resource.class))).thenReturn(resource);
        when(awsProvisioningService.provision(any(), any(), any())).thenReturn("i-12345");

        ResourceResponse response = resourceService.create(ec2Request, OPERATOR_USERNAME, UserRole.OPERATOR);

        assertThat(response.status()).isEqualTo(ResourceStatus.RUNNING);
    }

    @Test
    void create_persistsResourceTwice_whenAwsProvisioningSucceeds() {
        Resource resource = Instancio.of(ec2ResourceModel).create();
        when(resourceRepository.save(any(Resource.class))).thenReturn(resource);
        when(awsProvisioningService.provision(any(), any(), any())).thenReturn("i-12345");

        resourceService.create(ec2Request, OPERATOR_USERNAME, UserRole.OPERATOR);

        verify(resourceRepository, times(2)).save(any(Resource.class));
    }

    @Test
    void create_writesAuditLog_whenResourceIsCreated() {
        Resource resource = Instancio.of(ec2ResourceModel).create();
        when(resourceRepository.save(any(Resource.class))).thenReturn(resource);

        resourceService.create(ec2Request, OPERATOR_USERNAME, UserRole.OPERATOR);

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void create_throwsAccessDenied_whenCallerIsViewer() {
        assertThatThrownBy(() -> resourceService.create(ec2Request, VIEWER_USERNAME, UserRole.VIEWER))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void stop_setsStatusToStopped_whenResourceIsRunning() {
        Resource resource = Instancio.of(ec2ResourceModel)
                .set(field(Resource::getStatus), ResourceStatus.RUNNING)
                .create();
        UUID id = resource.getId();
        when(resourceRepository.findById(id)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(any(Resource.class))).thenReturn(resource);

        ResourceResponse response = resourceService.stop(id, OPERATOR_USERNAME, UserRole.OPERATOR);

        assertThat(response.status()).isEqualTo(ResourceStatus.STOPPED);
    }

    @Test
    void stop_throwsInvalidTransition_whenResourceIsPending() {
        Resource resource = Instancio.of(ec2ResourceModel)
                .set(field(Resource::getStatus), ResourceStatus.PENDING)
                .create();
        UUID id = resource.getId();
        when(resourceRepository.findById(id)).thenReturn(Optional.of(resource));

        assertThatThrownBy(() -> resourceService.stop(id, OPERATOR_USERNAME, UserRole.OPERATOR))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void stop_throwsAccessDenied_whenCallerIsViewer() {
        Resource resource = Instancio.of(ec2ResourceModel).create();
        UUID id = resource.getId();

        assertThatThrownBy(() -> resourceService.stop(id, VIEWER_USERNAME, UserRole.VIEWER))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void terminate_setsStatusToTerminated_whenResourceIsRunning() {
        Resource resource = Instancio.of(ec2ResourceModel)
                .set(field(Resource::getStatus), ResourceStatus.RUNNING)
                .create();
        UUID id = resource.getId();
        when(resourceRepository.findById(id)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(any(Resource.class))).thenReturn(resource);

        ResourceResponse response = resourceService.terminate(id, ADMIN_USERNAME, UserRole.ADMIN);

        assertThat(response.status()).isEqualTo(ResourceStatus.TERMINATED);
    }

    @Test
    void terminate_throwsInvalidTransition_whenResourceIsAlreadyTerminated() {
        Resource resource = Instancio.of(ec2ResourceModel)
                .set(field(Resource::getStatus), ResourceStatus.TERMINATED)
                .create();
        UUID id = resource.getId();
        when(resourceRepository.findById(id)).thenReturn(Optional.of(resource));

        assertThatThrownBy(() -> resourceService.terminate(id, ADMIN_USERNAME, UserRole.ADMIN))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void terminate_throwsAccessDenied_whenCallerIsViewer() {
        Resource resource = Instancio.of(ec2ResourceModel).create();
        UUID id = resource.getId();

        assertThatThrownBy(() -> resourceService.terminate(id, VIEWER_USERNAME, UserRole.VIEWER))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void findById_returnsResource_whenExists() {
        Resource resource = Instancio.of(ec2ResourceModel).create();
        UUID id = resource.getId();
        when(resourceRepository.findById(id)).thenReturn(Optional.of(resource));

        ResourceResponse response = resourceService.findById(id);

        assertThat(response.id()).isEqualTo(id);
    }

    @Test
    void findById_throwsResourceNotFound_whenDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(resourceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findAll_returnsAllResources_whenResourcesExist() {
        Resource resource = Instancio.of(ec2ResourceModel).create();
        when(resourceRepository.findAll()).thenReturn(List.of(resource));

        List<ResourceResponse> result = resourceService.findAll();

        assertThat(result).hasSize(1);
    }

    @Test
    void findAll_returnsEmptyList_whenNoResourcesExist() {
        when(resourceRepository.findAll()).thenReturn(List.of());

        List<ResourceResponse> result = resourceService.findAll();

        assertThat(result).isEmpty();
    }
}
