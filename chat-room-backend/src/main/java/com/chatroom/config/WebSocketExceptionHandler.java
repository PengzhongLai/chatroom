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

@ControllerAdvice
public class WebSocketExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketExceptionHandler.class);

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

    @MessageExceptionHandler(MessageConversionException.class)
    @SendToUser("/queue/errors")
    public Map<String, Object> handleMalformedPayload(MessageConversionException exception) {
        return error(400, "消息参数格式错误");
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public Map<String, Object> handleBusiness(BusinessException exception) {
        return error(exception.getErrorCode().getStatus().value(), exception.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public Map<String, Object> handleUnexpected(Exception exception) {
        log.error("Unhandled WebSocket message failure", exception);
        return error(500, "消息处理失败");
    }

    private Map<String, Object> error(int code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "ERROR");
        payload.put("code", code);
        payload.put("message", message);
        return payload;
    }
}
