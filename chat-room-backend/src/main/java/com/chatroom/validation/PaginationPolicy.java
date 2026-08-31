package com.chatroom.validation;

import com.chatroom.exception.BusinessException;

public final class PaginationPolicy {

    public static final int MAX_PAGE = 10_000;
    public static final int MAX_SIZE = 100;

    private PaginationPolicy() {
    }

    public static void validate(int page, int size) {
        if (page < 0 || page > MAX_PAGE) {
            throw BusinessException.badRequest("页码必须在 0 到 " + MAX_PAGE + " 之间");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw BusinessException.badRequest("每页数量必须在 1 到 " + MAX_SIZE + " 之间");
        }
    }
}
