package com.chatroom;

import com.chatroom.dto.request.ChannelCreateRequest;
import com.chatroom.dto.request.ChannelInviteRequest;
import com.chatroom.dto.request.ChannelMessageSendRequest;
import com.chatroom.dto.request.MessagePaginationRequest;
import com.chatroom.dto.request.PaginationRequest;
import com.chatroom.dto.request.PrivateMessageSendRequest;
import com.chatroom.dto.request.RegisterRequest;
import com.chatroom.dto.request.ThemeUpdateRequest;
import com.chatroom.enums.HistoryLevel;
import com.chatroom.enums.MessageType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RequestDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void paginationKeepsCompatibleDefaultsAndRejectsAbusiveValues() {
        PaginationRequest normal = new PaginationRequest();
        MessagePaginationRequest messages = new MessagePaginationRequest();
        assertThat(normal.getPage()).isZero();
        assertThat(normal.getSize()).isEqualTo(20);
        assertThat(messages.getSize()).isEqualTo(50);

        normal.setPage(-1);
        normal.setSize(101);
        assertThat(fields(validator.validate(normal))).contains("page", "size");
    }

    @Test
    void channelAndInviteRequestsEnforceDatabaseColumnBounds() {
        ChannelCreateRequest blankChannel = new ChannelCreateRequest("   ", "description", true);
        ChannelInviteRequest missingLimitedCount = new ChannelInviteRequest(
                7L, HistoryLevel.LIMITED, null
        );

        assertThat(fields(validator.validate(blankChannel))).contains("name");
        assertThat(fields(validator.validate(missingLimitedCount))).contains("historyLimitValid");
    }

    @Test
    void registrationAndThemeUseExplicitAllowLists() {
        RegisterRequest invalidUsername = new RegisterRequest("bad name", "123456", "nickname");
        ThemeUpdateRequest invalidTheme = new ThemeUpdateRequest("javascript");

        assertThat(fields(validator.validate(invalidUsername))).contains("username");
        assertThat(fields(validator.validate(invalidTheme))).contains("theme");
    }

    @Test
    void websocketTextMessageRequiresContentAndRejectsSystemSpoofing() {
        ChannelMessageSendRequest blankText = new ChannelMessageSendRequest(
                1L, "   ", MessageType.TEXT, null, null
        );
        ChannelMessageSendRequest forgedSystem = new ChannelMessageSendRequest(
                1L, "管理员通知", MessageType.SYSTEM, null, null
        );

        assertThat(fields(validator.validate(blankText))).contains("payloadValid");
        assertThat(fields(validator.validate(forgedSystem))).contains("payloadValid");
    }

    @Test
    void websocketAttachmentsRequireCanonicalStoredPaths() {
        PrivateMessageSendRequest invalid = new PrivateMessageSendRequest(
                1L,
                "",
                MessageType.FILE,
                "report.pdf",
                "/files/../../secret.pdf"
        );
        PrivateMessageSendRequest valid = new PrivateMessageSendRequest(
                1L,
                "",
                MessageType.FILE,
                "report.pdf",
                "/files/00000000-0000-4000-8000-000000000000.pdf"
        );

        assertThat(fields(validator.validate(invalid))).contains("filePath");
        assertThat(validator.validate(valid)).isEmpty();
    }

    private Set<String> fields(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}
