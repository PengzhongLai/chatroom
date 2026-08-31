package com.chatroom;

import com.chatroom.entity.User;
import com.chatroom.repository.UserRepository;
import com.chatroom.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    private User user;
    private String authorization;

    @BeforeEach
    void createAuthenticatedUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        user = userRepository.saveAndFlush(
                new User("stage2_" + suffix, "test-only-hash", "Stage 2")
        );
        authorization = "Bearer " + jwtTokenProvider.generateToken(user.getId(), user.getUsername());
    }

    @Test
    void requestBodyValidationReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        mockMvc.perform(post("/api/private-chats")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.targetUserId").exists());
    }

    @Test
    void malformedEnumsReturnStable400WithoutInternalDetails() throws Exception {
        mockMvc.perform(put("/api/users/status")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BUSY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数格式错误"));
    }

    @Test
    void paginationAndSearchBoundsAreEnforcedBeforeServicesRun() throws Exception {
        mockMvc.perform(get("/api/channels")
                        .header("Authorization", authorization)
                        .param("page", "-1")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.page").exists())
                .andExpect(jsonPath("$.errors.size").exists());

        mockMvc.perform(get("/api/search/messages")
                        .header("Authorization", authorization)
                        .param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.q").exists());

        mockMvc.perform(get("/api/private-chats/1/messages")
                        .header("Authorization", authorization)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").exists());
    }

    @Test
    void businessFailuresUseUnauthorizedConflictAndNotFoundStatuses() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"missing_user\",\"password\":\"123456\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user.getUsername()
                                + "\",\"password\":\"123456\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        mockMvc.perform(get("/api/channels/9223372036854775807")
                        .header("Authorization", authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void unknownResourcesAndUnsupportedMethodsKeepHttpSemantics() throws Exception {
        mockMvc.perform(get("/api/does-not-exist")
                        .header("Authorization", authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(post("/api/users/me")
                        .header("Authorization", authorization))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405));
    }
}
