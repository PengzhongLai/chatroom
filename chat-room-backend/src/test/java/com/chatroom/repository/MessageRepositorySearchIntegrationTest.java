package com.chatroom.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional(readOnly = true)
class MessageRepositorySearchIntegrationTest {

    @Autowired
    private MessageRepository messageRepository;

    @Test
    void accessibleSearchNativeQueryExecutesAgainstTestDatabase() {
        var result = messageRepository.searchAccessible(
                -1L,
                "__stage1_query_smoke_no_match__",
                PageRequest.of(0, 5)
        );

        assertThat(result).isEmpty();
    }
}

