package com.cloudresource.service;

import com.cloudresource.domain.*;
import com.cloudresource.dto.ResourceRequest;
import com.cloudresource.dto.ResourceResponse;
import com.cloudresource.exception.AccessDeniedException;
import com.cloudresource.exception.InvalidStateTransitionException;
import com.cloudresource.exception.ResourceNotFoundException;
import com.cloudresource.repository.AuditLogRepository;
import com.cloudresource.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceService {

    private static final String ACTION_CREATED = "CREATED";
    private static final String ACTION_STOPPED = "STOPPED";
    private static final String ACTION_STARTED = "STARTED";
    private static final String ACTION_TERMINATED = "TERMINATED";
    private static final String ACTION_STATUS_UPDATED = "STATUS_UPDATED";

    private final ResourceRepository resourceRepository;
    private final AuditLogRepository auditLogRepository;
    private final AwsProvisioningService awsProvisioningService;

    @Transactional
    public ResourceResponse create(ResourceRequest request, String username, UserRole role) {
        if (role == UserRole.VIEWER) {
            throw new AccessDeniedException("Viewers cannot create resources");
        }

        Resource resource = new Resource(request.name(), request.type(), request.region(), username);
        Resource saved = resourceRepository.save(resource);

        // Provision on AWS for supported types
        if (request.type() == ResourceType.EC2 || request.type() == ResourceType.S3) {
            try {
                String awsId = awsProvisioningService.provision(request.type(), request.name(), request.region());
                saved.setAwsResourceId(awsId);
                saved.setStatus(ResourceStatus.RUNNING);
                saved = resourceRepository.save(saved);
                auditLogRepository.save(new AuditLog(saved.getId(), username, ACTION_CREATED + ":AWS_ID=" + awsId));
                log.info("Provisioned {} on AWS: {}", request.type(), awsId);
            } catch (Exception e) {
                log.error("AWS provisioning failed for {}: {}", request.type(), e.getMessage());
                auditLogRepository.save(new AuditLog(saved.getId(), username, ACTION_CREATED + ":AWS_FAILED=" + e.getMessage()));
            }
        } else {
            auditLogRepository.save(new AuditLog(saved.getId(), username, ACTION_CREATED));
        }

        return toResponse(saved);
    }

    @Transactional
    public ResourceResponse updateStatus(UUID id, ResourceStatus newStatus, String username, UserRole role) {
        if (role == UserRole.VIEWER) {
            throw new AccessDeniedException("Viewers cannot update resource status");
        }
        Resource resource = findResourceById(id);
        ResourceStatus oldStatus = resource.getStatus();

        // Call AWS for EC2 state transitions
        if (resource.getAwsResourceId() != null) {
            try {
                if (newStatus == ResourceStatus.STOPPED && oldStatus == ResourceStatus.RUNNING) {
                    awsProvisioningService.stop(resource.getType(), resource.getAwsResourceId(), resource.getRegion());
                } else if (newStatus == ResourceStatus.RUNNING && oldStatus == ResourceStatus.STOPPED) {
                    awsProvisioningService.start(resource.getType(), resource.getAwsResourceId(), resource.getRegion());
                } else if (newStatus == ResourceStatus.TERMINATED) {
                    awsProvisioningService.terminate(resource.getType(), resource.getAwsResourceId(), resource.getRegion());
                }
            } catch (Exception e) {
                log.error("AWS status update failed: {}", e.getMessage());
                throw new RuntimeException("AWS operation failed: " + e.getMessage());
            }
        }

        resource.setStatus(newStatus);
        resource.setUpdatedAt(Instant.now());
        Resource saved = resourceRepository.save(resource);
        auditLogRepository.save(new AuditLog(id, username, ACTION_STATUS_UPDATED + ":" + oldStatus + "->" + newStatus));
        return toResponse(saved);
    }

    @Transactional
    public ResourceResponse stop(UUID id, String username, UserRole role) {
        if (role == UserRole.VIEWER) {
            throw new AccessDeniedException("Viewers cannot stop resources");
        }
        Resource resource = findResourceById(id);
        if (resource.getStatus() != ResourceStatus.RUNNING) {
            throw new InvalidStateTransitionException("Only RUNNING resources can be stopped");
        }

        if (resource.getAwsResourceId() != null) {
            awsProvisioningService.stop(resource.getType(), resource.getAwsResourceId(), resource.getRegion());
        }

        resource.setStatus(ResourceStatus.STOPPED);
        resource.setUpdatedAt(Instant.now());
        Resource saved = resourceRepository.save(resource);
        auditLogRepository.save(new AuditLog(id, username, ACTION_STOPPED));
        return toResponse(saved);
    }

    @Transactional
    public ResourceResponse terminate(UUID id, String username, UserRole role) {
        if (role == UserRole.VIEWER) {
            throw new AccessDeniedException("Viewers cannot terminate resources");
        }
        Resource resource = findResourceById(id);
        if (resource.getStatus() == ResourceStatus.TERMINATED) {
            throw new InvalidStateTransitionException("Resource is already terminated");
        }

        if (resource.getAwsResourceId() != null) {
            awsProvisioningService.terminate(resource.getType(), resource.getAwsResourceId(), resource.getRegion());
        }

        resource.setStatus(ResourceStatus.TERMINATED);
        resource.setUpdatedAt(Instant.now());
        Resource saved = resourceRepository.save(resource);
        auditLogRepository.save(new AuditLog(id, username, ACTION_TERMINATED));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ResourceResponse findById(UUID id) {
        return toResponse(findResourceById(id));
    }

    @Transactional(readOnly = true)
    public List<ResourceResponse> findAll() {
        return resourceRepository.findAll().stream().map(this::toResponse).toList();
    }

    private Resource findResourceById(UUID id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found: " + id));
    }

    private ResourceResponse toResponse(Resource resource) {
        return new ResourceResponse(
                resource.getId(),
                resource.getName(),
                resource.getType(),
                resource.getRegion(),
                resource.getStatus(),
                resource.getCreatedBy(),
                resource.getCreatedAt(),
                resource.getAwsResourceId()
        );
    }
}
