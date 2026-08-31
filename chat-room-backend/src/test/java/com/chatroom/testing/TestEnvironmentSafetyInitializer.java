package com.chatroom.testing;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Locale;

public final class TestEnvironmentSafetyInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String TEST_DATABASE_MARKER = "chat_room_test";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();
        boolean testProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equals("test") || profile.equals("test-mysql"));
        String datasourceUrl = environment.getProperty("spring.datasource.url", "")
                .toLowerCase(Locale.ROOT);

        if (!testProfile || !datasourceUrl.contains(TEST_DATABASE_MARKER)) {
            throw new IllegalStateException(
                    "Test startup blocked: an isolated chat_room_test database is required"
            );
        }
    }
}

