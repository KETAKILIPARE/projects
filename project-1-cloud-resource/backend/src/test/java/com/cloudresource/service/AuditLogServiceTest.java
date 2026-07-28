package com.cloudresource.service;

import com.cloudresource.domain.AuditLog;
import com.cloudresource.dto.AuditLogResponse;
import com.cloudresource.repository.AuditLogRepository;
import org.instancio.Instancio;
import org.instancio.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    private static final String ACTION_CREATED    = "CREATED";
    private static final String ACTION_TERMINATED = "TERMINATED";
    private static final String PERFORMED_BY      = "admin1";

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    private Model<AuditLog> auditLogModel;
    private UUID resourceId;

    @BeforeEach
    void setUp() {
        resourceId = UUID.randomUUID();

        auditLogModel = Instancio.of(AuditLog.class)
                .set(field(AuditLog::getResourceId),  resourceId)
                .set(field(AuditLog::getPerformedBy), PERFORMED_BY)
                .toModel();
    }

    @Test
    void findByResourceId_returnsLogs_whenLogsExist() {
        AuditLog log = Instancio.of(auditLogModel)
                .set(field(AuditLog::getAction), ACTION_CREATED)
                .create();
        when(auditLogRepository.findByResourceId(resourceId)).thenReturn(List.of(log));

        List<AuditLogResponse> result = auditLogService.findByResourceId(resourceId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).action()).isEqualTo(ACTION_CREATED);
    }

    @Test
    void findByResourceId_returnsEmpty_whenNoLogsExist() {
        when(auditLogRepository.findByResourceId(resourceId)).thenReturn(List.of());

        List<AuditLogResponse> result = auditLogService.findByResourceId(resourceId);

        assertThat(result).isEmpty();
    }

    @Test
    void findByResourceId_returnsMultipleLogs_whenMultipleActionsExist() {
        AuditLog created    = Instancio.of(auditLogModel).set(field(AuditLog::getAction), ACTION_CREATED).create();
        AuditLog terminated = Instancio.of(auditLogModel).set(field(AuditLog::getAction), ACTION_TERMINATED).create();
        when(auditLogRepository.findByResourceId(resourceId)).thenReturn(List.of(created, terminated));

        List<AuditLogResponse> result = auditLogService.findByResourceId(resourceId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AuditLogResponse::action)
                .containsExactly(ACTION_CREATED, ACTION_TERMINATED);
    }
}
