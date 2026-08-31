package com.chatroom.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MessageSearchQuery extends PaginationRequest {

    @NotBlank(message = "搜索关键词不能为空")
    @Size(max = 100, message = "搜索关键词不能超过 100 个字符")
    private String q;

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }
}
