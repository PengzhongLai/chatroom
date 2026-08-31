package com.chatroom.controller;

import com.chatroom.dto.ApiResponse;
import com.chatroom.dto.request.MessageSearchQuery;
import com.chatroom.dto.response.SearchMessageResponse;
import com.chatroom.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/messages")
    public ApiResponse<List<SearchMessageResponse>> search(
            @Valid @ModelAttribute MessageSearchQuery query) {
        return ApiResponse.success(searchService.searchMessages(
                query.getQ(), query.getPage(), query.getSize()
        ));
    }
}
