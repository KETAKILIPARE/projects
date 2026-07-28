package com.workflow.controller;

import com.workflow.domain.TaskStatus;
import com.workflow.dto.TaskRequest;
import com.workflow.dto.TaskResponse;
import com.workflow.dto.TaskStatusUpdateRequest;
import com.workflow.exception.TaskNotFoundException;
import com.workflow.service.TaskService;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    private static final String MEMBER_USERNAME = "member1";
    private static final String TASK_TITLE      = "Fix login bug";
    private static final String TASK_DETAILS    = "Details";

    @Mock private TaskService taskService;

    @InjectMocks
    private TaskController taskController;

    private final UserDetails authenticatedUser = User.withUsername(MEMBER_USERNAME)
            .password("irrelevant")
            .roles("USER")
            .build();

    private TaskResponse taskResponse(TaskStatus status) {
        return Instancio.of(TaskResponse.class)
                .set(field(TaskResponse::status), status)
                .create();
    }

    @Test
    void create_shouldReturn201_whenRequestIsValid() {
        TaskRequest request = new TaskRequest(TASK_TITLE, TASK_DETAILS, UUID.randomUUID(), null);
        when(taskService.create(any(), eq(MEMBER_USERNAME))).thenReturn(taskResponse(TaskStatus.TODO));

        ResponseEntity<TaskResponse> result = taskController.create(request, authenticatedUser);

        assertThat(result.getStatusCode())
                .as("should return 201 CREATED for a valid task creation request")
                .isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().status())
                .as("newly created task should have TODO status")
                .isEqualTo(TaskStatus.TODO);
    }

    @Test
    void getById_shouldReturnTask_whenTaskExists() {
        UUID taskId = UUID.randomUUID();
        TaskResponse expected = taskResponse(TaskStatus.TODO);
        when(taskService.findById(taskId)).thenReturn(expected);

        ResponseEntity<TaskResponse> result = taskController.getById(taskId);

        assertThat(result.getStatusCode())
                .as("should return 200 OK when task is found")
                .isEqualTo(HttpStatus.OK);
        assertThat(result.getBody())
                .as("response body should match the task returned by the service")
                .isEqualTo(expected);
    }

    @Test
    void getById_shouldPropagateException_whenTaskNotFound() {
        UUID taskId = UUID.randomUUID();
        when(taskService.findById(taskId)).thenThrow(new TaskNotFoundException("Not found"));

        assertThatThrownBy(() -> taskController.getById(taskId))
                .as("should propagate TaskNotFoundException when task does not exist")
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void getByWorkspace_shouldReturnTaskList() {
        UUID workspaceId = UUID.randomUUID();
        List<TaskResponse> tasks = List.of(taskResponse(TaskStatus.TODO), taskResponse(TaskStatus.IN_PROGRESS));
        when(taskService.findByWorkspaceId(workspaceId)).thenReturn(tasks);

        ResponseEntity<List<TaskResponse>> result = taskController.getByWorkspace(workspaceId);

        assertThat(result.getStatusCode())
                .as("should return 200 OK for workspace task list")
                .isEqualTo(HttpStatus.OK);
        assertThat(result.getBody())
                .as("should return all tasks for the workspace")
                .hasSize(2);
    }

    @Test
    void updateStatus_shouldReturn200_whenTransitionIsValid() {
        UUID taskId = UUID.randomUUID();
        TaskStatusUpdateRequest request = new TaskStatusUpdateRequest(TaskStatus.IN_PROGRESS);
        when(taskService.updateStatus(eq(taskId), any(), eq(MEMBER_USERNAME)))
                .thenReturn(taskResponse(TaskStatus.IN_PROGRESS));

        ResponseEntity<TaskResponse> result = taskController.updateStatus(taskId, request, authenticatedUser);

        assertThat(result.getStatusCode())
                .as("should return 200 OK for a valid status update")
                .isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().status())
                .as("response should reflect the updated status")
                .isEqualTo(TaskStatus.IN_PROGRESS);
    }
}
