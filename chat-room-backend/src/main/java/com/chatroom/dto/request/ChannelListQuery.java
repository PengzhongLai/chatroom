package com.chatroom.dto.request;

import jakarta.validation.constraints.Size;

public class ChannelListQuery extends PaginationRequest {

    @Size(max = 100, message = "频道搜索关键词不能超过 100 个字符")
    private String keyword;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
