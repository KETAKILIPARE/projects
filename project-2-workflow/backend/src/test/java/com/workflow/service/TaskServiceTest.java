package com.workflow.service;

import com.workflow.domain.Task;
import com.workflow.domain.TaskStatus;
import com.workflow.dto.TaskRequest;
import com.workflow.dto.TaskResponse;
import com.workflow.dto.TaskStatusUpdateRequest;
import com.workflow.exception.InvalidTaskTransitionException;
import com.workflow.exception.NotWorkspaceMemberException;
import com.workflow.exception.TaskNotFoundException;
import com.workflow.exception.WorkspaceNotFoundException;
import com.workflow.repository.TaskRepository;
import com.workflow.repository.WorkspaceMemberRepository;
import com.workflow.repository.WorkspaceRepository;
import org.instancio.Instancio;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final String MEMBER_USERNAME  = "member1";
    private static final String OUTSIDER_USERNAME = "outsider";
    private static final String TASK_TITLE       = "Fix login bug";
    private static final String TASK_DESCRIPTION = "Details here";

    @Mock private TaskRepository taskRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private WorkspaceMemberRepository memberRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private TaskService taskService;

    private UUID workspaceId;
    private Task savedTask;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        savedTask = Instancio.of(Task.class)
                .set(field(Task::getTitle), TASK_TITLE)
                .set(field(Task::getDescription), TASK_DESCRIPTION)
                .set(field(Task::getWorkspaceId), workspaceId)
                .set(field(Task::getCreatedBy), MEMBER_USERNAME)
                .set(field(Task::getStatus), TaskStatus.TODO)
                .create();
    }

    @Test
    void create_shouldReturnTaskWithTodoStatus_whenMemberIsValid() {
        TaskRequest request = new TaskRequest(TASK_TITLE, TASK_DESCRIPTION, workspaceId, null);
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(memberRepository.existsByWorkspaceIdAndUsername(workspaceId, MEMBER_USERNAME)).thenReturn(true);
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        TaskResponse response = taskService.create(request, MEMBER_USERNAME);

        assertThat(response.status())
                .as("newly created task should have TODO status")
                .isEqualTo(TaskStatus.TODO);
    }

    @Test
    void create_shouldThrowWorkspaceNotFound_whenWorkspaceDoesNotExist() {
        TaskRequest request = new TaskRequest(TASK_TITLE, TASK_DESCRIPTION, workspaceId, null);
        when(workspaceRepository.existsById(workspaceId)).thenReturn(false);

        assertThatThrownBy(() -> taskService.create(request, MEMBER_USERNAME))
                .as("should throw WorkspaceNotFoundException when workspace does not exist")
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    @Test
    void create_shouldThrowNotWorkspaceMember_whenUserIsNotMember() {
        TaskRequest request = new TaskRequest(TASK_TITLE, TASK_DESCRIPTION, workspaceId, null);
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(memberRepository.existsByWorkspaceIdAndUsername(workspaceId, OUTSIDER_USERNAME)).thenReturn(false);

        assertThatThrownBy(() -> taskService.create(request, OUTSIDER_USERNAME))
                .as("should throw NotWorkspaceMemberException when user is not a member")
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void updateStatus_shouldTransitionFromTodoToInProgress() {
        savedTask.setStatus(TaskStatus.TODO);
        UUID taskId = savedTask.getId();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(savedTask));
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        TaskResponse response = taskService.updateStatus(taskId, new TaskStatusUpdateRequest(TaskStatus.IN_PROGRESS), MEMBER_USERNAME);

        assertThat(response.status())
                .as("status should transition from TODO to IN_PROGRESS")
                .isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void updateStatus_shouldTransitionFromInProgressToReview() {
        savedTask.setStatus(TaskStatus.IN_PROGRESS);
        UUID taskId = savedTask.getId();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(savedTask));
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        TaskResponse response = taskService.updateStatus(taskId, new TaskStatusUpdateRequest(TaskStatus.REVIEW), MEMBER_USERNAME);

        assertThat(response.status())
                .as("status should transition from IN_PROGRESS to REVIEW")
                .isEqualTo(TaskStatus.REVIEW);
    }

    @Test
    void updateStatus_shouldTransitionFromReviewToDone() {
        savedTask.setStatus(TaskStatus.REVIEW);
        UUID taskId = savedTask.getId();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(savedTask));
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        TaskResponse response = taskService.updateStatus(taskId, new TaskStatusUpdateRequest(TaskStatus.DONE), MEMBER_USERNAME);

        assertThat(response.status())
                .as("status should transition from REVIEW to DONE")
                .isEqualTo(TaskStatus.DONE);
    }

    @Test
    void updateStatus_shouldThrowInvalidTransition_whenSameStatus() {
        savedTask.setStatus(TaskStatus.TODO);
        UUID taskId = savedTask.getId();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(savedTask));

        assertThatThrownBy(() -> taskService.updateStatus(taskId, new TaskStatusUpdateRequest(TaskStatus.TODO), MEMBER_USERNAME))
                .as("should throw InvalidTaskTransitionException when transitioning to the same status")
                .isInstanceOf(InvalidTaskTransitionException.class);
    }

    @Test
    void updateStatus_shouldBroadcastNotification_whenStatusChanges() {
        savedTask.setStatus(TaskStatus.TODO);
        UUID taskId = savedTask.getId();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(savedTask));
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        taskService.updateStatus(taskId, new TaskStatusUpdateRequest(TaskStatus.IN_PROGRESS), MEMBER_USERNAME);

        verify(notificationService).broadcastTaskUpdate(any());
    }

    @Test
    void delete_shouldDeleteTask_whenTaskExists() {
        UUID taskId = savedTask.getId();
        when(taskRepository.existsById(taskId)).thenReturn(true);

        taskService.delete(taskId);

        verify(taskRepository).deleteById(taskId);
    }

    @Test
    void delete_shouldThrowTaskNotFound_whenTaskDoesNotExist() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.existsById(taskId)).thenReturn(false);

        assertThatThrownBy(() -> taskService.delete(taskId))
                .as("should throw TaskNotFoundException when task does not exist")
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void findByWorkspaceId_shouldReturnAllTasksInWorkspace() {
        when(taskRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(savedTask));

        List<TaskResponse> result = taskService.findByWorkspaceId(workspaceId);

        assertThat(result)
                .as("should return all tasks belonging to the workspace")
                .hasSize(1);
    }

    @Test
    void findByWorkspaceId_shouldReturnEmptyList_whenNoTasksExist() {
        when(taskRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of());

        List<TaskResponse> result = taskService.findByWorkspaceId(workspaceId);

        assertThat(result)
                .as("should return empty list when workspace has no tasks")
                .isEmpty();
    }

    @Test
    void findById_shouldThrowTaskNotFound_whenDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.findById(id))
                .as("should throw TaskNotFoundException when task does not exist")
                .isInstanceOf(TaskNotFoundException.class);
    }
}
