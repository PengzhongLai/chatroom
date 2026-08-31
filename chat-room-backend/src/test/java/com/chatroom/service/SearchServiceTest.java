package com.chatroom.service;

import com.chatroom.repository.MessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchServiceTest {

    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final SearchService searchService = new SearchService(messageRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void alwaysQueriesThroughUserScopedRepositoryMethod() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(42L, null, List.of())
        );
        when(messageRepository.searchAccessible(42L, "100!%!_safe", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of()));

        searchService.searchMessages(" 100%_safe ", 0, 20);

        verify(messageRepository).searchAccessible(42L, "100!%!_safe", PageRequest.of(0, 20));
    }

    @Test
    void rejectsInvalidPaginationBeforeQueryingDatabase() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(42L, null, List.of())
        );

        assertThatThrownBy(() -> searchService.searchMessages("keyword", -1, 20))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("页码");
        assertThatThrownBy(() -> searchService.searchMessages("keyword", 0, 101))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("每页");
    }
}
