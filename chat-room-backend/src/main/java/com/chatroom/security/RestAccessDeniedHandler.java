package com.chatroom.security;

import com.chatroom.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 授权失败（403）的响应处理。
 * 在身份已知但无权访问该 URL 时被调用，返回项目统一的 JSON 信封。
 * 与业务层的 BusinessException.forbidden 是两条不同路径，但输出格式一致。
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    /** 用于把 ApiResponse 序列化成 JSON */
    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 返回 HTTP 403 和统一格式的错误 JSON */
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(403, "没有权限执行此操作"));
    }
}
