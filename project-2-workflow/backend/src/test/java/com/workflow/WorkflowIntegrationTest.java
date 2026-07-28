package com.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.domain.SystemRole;
import com.workflow.domain.TaskStatus;
import com.workflow.domain.WorkspaceRole;
import com.workflow.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class WorkflowIntegrationTest {

    private static final String ALICE            = "alice";
    private static final String BOB              = "bob";
    private static final String CHARLIE          = "charlie";
    private static final String NOBODY           = "nobody";
    private static final String PASSWORD         = "password123";
    private static final String WRONG_PASSWORD   = "wrong";
    private static final String WORKSPACE_NAME   = "My Team";
    private static final String ALICE_TEAM       = "Alice Team";
    private static final String TASK_TITLE       = "Fix bug";
    private static final String TASK_DESCRIPTION = "Details";
    private static final String TASK_TO_DELETE   = "Delete me";
    private static final String TASK_PROTECTED   = "Protected";
    private static final String TASK_MY_TASK     = "My task";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setup() throws Exception {
        register(ALICE, SystemRole.SYSTEM_ADMIN);
        register(BOB, SystemRole.SYSTEM_MEMBER);
        aliceToken = login(ALICE);
        bobToken = login(BOB);
    }

    private void register(String username, SystemRole role) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(username, PASSWORD, role))))
                .andExpect(status().isCreated());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new LoginRequest(username, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    private String createWorkspace(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/workspaces")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    private void addMember(String adminToken, String wsId, String username, WorkspaceRole role) throws Exception {
        mockMvc.perform(post("/api/workspaces/" + wsId + "/members")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddMemberRequest(username, role))))
                .andExpect(status().isCreated());
    }

    private String createTask(String token, String wsId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/workspaces/" + wsId + "/tasks")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateTaskRequest(title, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    @Test
    void register_shouldReturn409_whenUsernameAlreadyTaken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegisterRequest(ALICE, PASSWORD, SystemRole.SYSTEM_ADMIN))))
                .andExpect(status().isConflict());
    }

    @Test
    void login_shouldReturn401_whenWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(ALICE, WRONG_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createWorkspace_shouldReturn201_whenSystemAdmin() throws Exception {
        mockMvc.perform(post("/api/workspaces")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + WORKSPACE_NAME + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(WORKSPACE_NAME));
    }

    @Test
    void createWorkspace_shouldReturn403_whenSystemMember() throws Exception {
        mockMvc.perform(post("/api/workspaces")
                .header("Authorization", "Bearer " + bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + WORKSPACE_NAME + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createWorkspace_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + WORKSPACE_NAME + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getWorkspaces_shouldOnlyShowWorkspacesUserIsMemberOf() throws Exception {
        createWorkspace(aliceToken, ALICE_TEAM);

        mockMvc.perform(get("/api/workspaces")
                .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/workspaces")
                .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getWorkspaces_shouldShowWorkspace_afterBeingInvited() throws Exception {
        String wsId = createWorkspace(aliceToken, ALICE_TEAM);
        addMember(aliceToken, wsId, BOB, WorkspaceRole.MEMBER);

        mockMvc.perform(get("/api/workspaces")
                .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void addMember_shouldReturn404_whenUserDoesNotExist() throws Exception {
        String wsId = createWorkspace(aliceToken, WORKSPACE_NAME);
        mockMvc.perform(post("/api/workspaces/" + wsId + "/members")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddMemberRequest(NOBODY, WorkspaceRole.MEMBER))))
                .andExpect(status().isNotFound());
    }

    @Test
    void addMember_shouldReturn409_whenAlreadyMember() throws Exception {
        String wsId = createWorkspace(aliceToken, WORKSPACE_NAME);
        addMember(aliceToken, wsId, BOB, WorkspaceRole.MEMBER);
        mockMvc.perform(post("/api/workspaces/" + wsId + "/members")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddMemberRequest(BOB, WorkspaceRole.MEMBER))))
                .andExpect(status().isConflict());
    }

    @Test
    void addMember_shouldReturn403_whenCallerIsMemberNotAdmin() throws Exception {
        String wsId = createWorkspace(aliceToken, WORKSPACE_NAME);
        addMember(aliceToken, wsId, BOB, WorkspaceRole.MEMBER);
        register(CHARLIE, SystemRole.SYSTEM_MEMBER);

        mockMvc.perform(post("/api/workspaces/" + wsId + "/members")
                .header("Authorization", "Bearer " + bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddMemberRequest(CHARLIE, WorkspaceRole.MEMBER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeMember_shouldReturn204_andMemberLosesAccess() throws Exception {
        String wsId = createWorkspace(aliceToken, WORKSPACE_NAME);
        addMember(aliceToken, wsId, BOB, WorkspaceRole.MEMBER);

        mockMvc.perform(delete("/api/workspaces/" + wsId + "/members/" + BOB)
                .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/workspaces")
                .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void removeMember_shouldReturn403_whenCallerIsMember() throws Exception {
        String wsId = createWorkspace(aliceToken, WORKSPACE_NAME);
        addMember(aliceToken, wsId, BOB, WorkspaceRole.MEMBER);

        mockMvc.perform(delete("/api/workspaces/" + wsId + "/members/" + ALICE)
                .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTask_shouldReturn201_withTodoStatus_whenMemberCreates() throws Exception {
        String wsId = createWorkspace(aliceToken, WORKSPACE_NAME);
        addMember(aliceToken, wsId, BOB, WorkspaceRole.MEMBER);

        mockMvc.perform(post("/api/workspaces/" + wsId + "/tasks")
                .header("Authorization", "Bearer " + bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateTaskRequest(TASK_TITLE, TASK_DESCRIPTION, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(TASK_TITLE))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void createTask_shouldReturn403_whenNonMemberTriesToCreate() throws Exception {
        String wsId = createWorkspace(aliceToken, WORKSPACE_NAME);

        mockMvc.perform(post("/api/workspaces/" + wsId + "/tasks")
                .header("Authorization", "Bearer " + bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateTaskRequest(TASK_TITLE, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateTaskStatus_shouldReturn200_whenMemberUpdates() throws Exception {
        String wsId = createWorkspace(aliceToken, WORKSPACE_NAME);
        String taskId = createTask(aliceToken, wsId, TASK_MY_TASK);

        mockMvc.perform(patch("/api/workspaces/" + wsId + "/tasks/" + taskId + "/status")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TaskStatusUpdateRequest(TaskStatus.IN_PROGRESS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void deleteTask_shouldReturn204_whenAdminDeletes() throws Exception {
        String wsId = createWorkspace(aliceToken, WORKSPACE_NAME);
        String taskId = createTask(aliceToken, wsId, TASK_TO_DELETE);

        mockMvc.perform(delete("/api/workspaces/" + wsId + "/tasks/" + taskId)
                .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteTask_shouldReturn403_whenMemberTriesToDelete() throws Exception {
        String wsId = createWorkspace(aliceToken, WORKSPACE_NAME);
        addMember(aliceToken, wsId, BOB, WorkspaceRole.MEMBER);
        String taskId = createTask(aliceToken, wsId, TASK_PROTECTED);

        mockMvc.perform(delete("/api/workspaces/" + wsId + "/tasks/" + taskId)
                .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTasks_shouldReturn403_whenNonMemberTriesToView() throws Exception {
        String wsId = createWorkspace(aliceToken, WORKSPACE_NAME);

        mockMvc.perform(get("/api/workspaces/" + wsId + "/tasks")
                .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());
    }
}
