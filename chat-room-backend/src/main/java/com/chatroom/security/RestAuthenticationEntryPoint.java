package com.chatroom.security;

import com.chatroom.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 认证失败（401）的响应处理。
 * 在缺少 token、token 无效或过期时被调用，返回项目统一的 JSON 信封。
 * 目的是替代 Spring Security 默认的 HTML 登录页，让前端能解析错误信息。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** 用于把 ApiResponse 序列化成 JSON */
    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 返回 HTTP 401 和统一格式的错误 JSON */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authenticationException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(401, "请先登录或重新登录"));
    }
}
