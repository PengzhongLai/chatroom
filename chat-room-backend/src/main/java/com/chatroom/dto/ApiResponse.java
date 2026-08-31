package com.chatroom.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String> errors;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> ApiResponse<T> error(int code, String message, Map<String, String> errors) {
        ApiResponse<T> r = error(code, message);
        r.errors = errors;
        return r;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public Map<String, String> getErrors() { return errors; }
    public void setErrors(Map<String, String> errors) { this.errors = errors; }
}
