package com.chatroom.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserSearchQuery {

    @NotBlank(message = "用户搜索关键词不能为空")
    @Size(max = 50, message = "用户搜索关键词不能超过 50 个字符")
    private String q;

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }
}
