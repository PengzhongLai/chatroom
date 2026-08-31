package com.chatroom.testing;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestEnvironmentSafetyInitializerTest {

    private final TestEnvironmentSafetyInitializer initializer =
            new TestEnvironmentSafetyInitializer();

    @Test
    void acceptsExplicitTestProfileAndTestDatabase() {
        GenericApplicationContext context = context(
                "test-mysql",
                "jdbc:mysql://127.0.0.1:3306/chat_room_test"
        );

        assertThatCode(() -> initializer.initialize(context)).doesNotThrowAnyException();
    }

    @Test
    void blocksDevelopmentDatabaseBeforeApplicationContextStarts() {
        GenericApplicationContext context = context(
                "dev",
                "jdbc:mysql://127.0.0.1:3306/chat_room"
        );

        assertThatThrownBy(() -> initializer.initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chat_room_test");
    }

    private GenericApplicationContext context(String profile, String datasourceUrl) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", datasourceUrl);
        environment.setActiveProfiles(profile);
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);
        return context;
    }
}

