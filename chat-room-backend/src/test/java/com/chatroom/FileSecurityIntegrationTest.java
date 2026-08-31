package com.chatroom;

import com.chatroom.entity.User;
import com.chatroom.repository.UserRepository;
import com.chatroom.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FileSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    private String validToken;

    @BeforeEach
    void createAuthenticatedUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User("stage1_" + suffix, "unused-test-hash", "Stage 1");
        user = userRepository.saveAndFlush(user);
        validToken = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
    }

    @Test
    void anonymousUploadAndDownloadAreRejectedWith401() throws Exception {
        MockMultipartFile png = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
        );

        mockMvc.perform(multipart("/api/files/upload").file(png))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/files/00000000-0000-4000-8000-000000000000.png"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(put("/api/users/theme")
                        .contentType("application/json")
                        .content("{\"theme\":\"dark\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void invalidOrDeletedUserTokensAreRejectedWith401() throws Exception {
        mockMvc.perform(get("/files/00000000-0000-4000-8000-000000000000.png")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        String nonexistentUserToken = jwtTokenProvider.generateToken(
                Long.MAX_VALUE,
                "deleted-user"
        );
        mockMvc.perform(get("/files/00000000-0000-4000-8000-000000000000.png")
                        .header("Authorization", "Bearer " + nonexistentUserToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestReachesProtectedDownloadController() throws Exception {
        mockMvc.perform(get("/files/00000000-0000-4000-8000-000000000000.png")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void authenticatedSvgUploadIsRejectedBeforeWritingFile() throws Exception {
        MockMultipartFile svg = new MockMultipartFile(
                "file",
                "payload.svg",
                "image/svg+xml",
                "<svg onload=\"alert(1)\"/>".getBytes()
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(svg)
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不支持的文件扩展名"));
    }
}
