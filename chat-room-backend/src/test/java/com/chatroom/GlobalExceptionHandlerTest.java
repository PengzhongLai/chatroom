package com.chatroom;

import com.chatroom.config.GlobalExceptionHandler;
import com.chatroom.dto.ApiResponse;
import com.chatroom.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionPreservesItsHttpSemantics() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(
                BusinessException.forbidden("需要管理员权限")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(403);
        assertThat(response.getBody().getMessage()).isEqualTo("需要管理员权限");
    }

    @Test
    void unexpectedExceptionsDoNotLeakInternalMessages() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnexpected(
                new IllegalStateException("jdbc:mysql://secret-host/internal")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("服务器内部错误");
        assertThat(response.getBody().getMessage()).doesNotContain("secret-host");
    }
}
