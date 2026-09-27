package com.chatroom.config;

import com.chatroom.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebSocket 消息处理异常的统一出口。
 *
 * 关键点：这里处理的是 @MessageMapping 方法**内部**抛出的异常，
 * 通过 @SendToUser("/queue/errors") 推给该用户自己的队列——**不会关闭连接**。
 *
 * 与 StompInterceptor 抛异常的区别（两者行为完全不同）：
 * - 拦截器抛异常 → Spring 发 STOMP ERROR 帧 → SockJS 关闭整个连接（c[1002]）
 * - 本类处理的异常 → 转成 JSON 推到 /user/queue/errors，连接保持存活
 *
 * 所以"业务错误"走队列、"授权错误"走 ERROR 帧，前端要分别处理。
 */
@ControllerAdvice
public class WebSocketExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketExceptionHandler.class);

    /** 参数校验失败（如 content 为空、type 与附件不相容） */
    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/queue/errors")
    public Map<String, Object> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("消息参数校验失败");
        return error(400, message);
    }

    /** 载荷格式错误（如 JSON 解析失败、字段类型不匹配） */
    @MessageExceptionHandler(MessageConversionException.class)
    @SendToUser("/queue/errors")
    public Map<String, Object> handleMalformedPayload(MessageConversionException exception) {
        return error(400, "消息参数格式错误");
    }

    /**
     * 业务异常（如频道禁言、私聊未激活、撤回非本人消息）。
     * 这类错误只影响当前操作，连接继续可用。
     */
    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public Map<String, Object> handleBusiness(BusinessException exception) {
        return error(exception.getErrorCode().getStatus().value(), exception.getMessage());
    }

    /** 兜底：未预期的异常。记日志但不把堆栈暴露给客户端 */
    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public Map<String, Object> handleUnexpected(Exception exception) {
        log.error("Unhandled WebSocket message failure", exception);
        return error(500, "消息处理失败");
    }

    /** 统一错误载荷形状：{type:"ERROR", code, message} */
    private Map<String, Object> error(int code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "ERROR");
        payload.put("code", code);
        payload.put("message", message);
        return payload;
    }
}
