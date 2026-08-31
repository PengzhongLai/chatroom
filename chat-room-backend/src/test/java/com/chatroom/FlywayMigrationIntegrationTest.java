package com.chatroom;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FlywayMigrationIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    void testProfileNeverUsesDevelopmentDatabase() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            assertThat(url).contains("chat_room_test");
            assertThat(url).doesNotContain("/chat_room?");
        }
    }

    @Test
    void flywayBuildsSchemaFromZeroAndHibernateAcceptsIt() throws SQLException {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");

        Set<String> tables = new HashSet<>();
        try (var connection = dataSource.getConnection();
             var resultSet = connection.getMetaData().getTables(
                     connection.getCatalog(), null, "%", new String[] {"TABLE"})) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        }

        assertThat(tables).contains(
                "channel_members",
                "channels",
                "flyway_schema_history",
                "message_reads",
                "messages",
                "private_chats",
                "users"
        );
    }
}
