package com.chatroom.security;

import com.chatroom.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器。每个 HTTP 请求都经过它，负责"识别身份"：
 * 从 Authorization 头取出 Bearer token，验签并确认用户仍存在后，
 * 把 userId 写入 SecurityContextHolder，供后续 Service 读取。
 *
 * 注意它只负责"识别"，不负责"拒绝"——是否放行由 SecurityConfig 的授权规则决定。
 * 没有 token 时它什么都不做（不报错），把请求继续交给后面的过滤器。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 令牌的校验与解析 */
    private final JwtTokenProvider jwtTokenProvider;
    /** 认证失败时输出 401 JSON 响应 */
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    /** 用于确认令牌里的用户仍然存在 */
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                   UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.userRepository = userRepository;
    }

    /**
     * 请求进入时的处理流程：
     * ① 没有 token → 跳过，直接放行（匿名请求）
     * ② token 无效（签名错/过期）→ 清空上下文并返回 401
     * ③ token 有效但用户已不存在 → 返回 401
     * ④ token 有效且用户存在 → 把 userId 写入 SecurityContextHolder
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            // 签名错误或已过期：直接返回 401，不进入 Controller
            if (!jwtTokenProvider.validateToken(token)) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(request, response, null);
                return;
            }

            try {
                Long userId = jwtTokenProvider.getUserId(token);
                // 令牌有效期 7 天，期间用户可能已被删除，所以每次都查库确认
                if (userId == null || userId <= 0 || !userRepository.existsById(userId)) {
                    authenticationEntryPoint.commence(request, response, null);
                    return;
                }
                // principal 放 Long 类型的 userId（不是 User 对象）；
                // 权限列表为空——频道角色不走 Spring Security，由业务层查成员表判断
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ex) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(request, response, null);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /** 从 Authorization 头提取 token。只认 "Bearer " 前缀（大小写敏感），否则视为无 token */
    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
