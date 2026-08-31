package com.chatroom.dto.request;

import com.chatroom.validation.PaginationPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class PaginationRequest {

    @Min(value = 0, message = "页码不能小于 0")
    @Max(value = PaginationPolicy.MAX_PAGE, message = "页码不能超过 10000")
    private int page = 0;

    @Min(value = 1, message = "每页数量不能小于 1")
    @Max(value = PaginationPolicy.MAX_SIZE, message = "每页数量不能超过 100")
    private int size;

    public PaginationRequest() {
        this(20);
    }

    protected PaginationRequest(int defaultSize) {
        this.size = defaultSize;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
