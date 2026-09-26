package com.chatroom.config;

import com.chatroom.security.JwtAuthenticationFilter;
import com.chatroom.security.RestAccessDeniedHandler;
import com.chatroom.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security 配置：URL 级授权规则 + 密码编码器。
 * 采用无状态 JWT 模式，不用服务端 session。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 负责识别身份的过滤器，要插到过滤器链里 */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    /** 跨域配置 */
    private final CorsConfigurationSource corsConfigurationSource;
    /** 认证失败（401）的响应处理 */
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    /** 授权失败（403）的响应处理 */
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          CorsConfigurationSource corsConfigurationSource,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsConfigurationSource = corsConfigurationSource;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /** 定义过滤器链：跨域、无状态、两个失败处理器、URL 授权规则，并插入 JWT 过滤器 */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())          // 无 Cookie 会话，不存在 CSRF 攻击面
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)   // 401 时返回 JSON
                .accessDeniedHandler(accessDeniedHandler)             // 403 时返回 JSON
            )
            .authorizeHttpRequests(auth -> auth
                // 登录注册必须放行，否则用户拿不到第一个 token（死锁）；
                // /ws/** 放行的是 WebSocket 握手，真正的认证在 STOMP CONNECT 帧里做
                .requestMatchers("/api/auth/**", "/ws/**").permitAll()
                .anyRequest().authenticated()      // 其余接口一律要求已认证
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 密码编码器：BCrypt，自带随机盐，同一密码每次 encode 结果不同 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
