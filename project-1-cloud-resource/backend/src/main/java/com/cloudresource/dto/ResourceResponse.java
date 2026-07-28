package com.cloudresource.dto;

import com.cloudresource.domain.ResourceStatus;
import com.cloudresource.domain.ResourceType;

import java.time.Instant;
import java.util.UUID;

public record ResourceResponse(
        UUID id,
        String name,
        ResourceType type,
        String region,
        ResourceStatus status,
        String createdBy,
        Instant createdAt,
        String awsResourceId
) {}
