CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    avatar_url VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    nickname VARCHAR(50) NULL,
    password VARCHAR(255) NOT NULL,
    status ENUM('INVISIBLE', 'OFFLINE', 'ONLINE') NOT NULL,
    username VARCHAR(50) NOT NULL,
    theme VARCHAR(10) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE channels (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    description VARCHAR(255) NULL,
    invite_code VARCHAR(20) NULL,
    is_muted BIT(1) NOT NULL,
    is_public BIT(1) NOT NULL,
    name VARCHAR(100) NOT NULL,
    creator_id BIGINT NOT NULL,
    CONSTRAINT pk_channels PRIMARY KEY (id),
    CONSTRAINT uk_channels_invite_code UNIQUE (invite_code),
    CONSTRAINT fk_channels_creator
        FOREIGN KEY (creator_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE channel_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    history_level ENUM('ALL', 'LIMITED', 'NONE') NOT NULL,
    history_limit INT NULL,
    joined_at DATETIME(6) NOT NULL,
    role ENUM('ADMIN', 'CREATOR', 'MEMBER') NOT NULL,
    channel_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT pk_channel_members PRIMARY KEY (id),
    CONSTRAINT uk_channel_members_channel_user UNIQUE (channel_id, user_id),
    CONSTRAINT fk_channel_members_channel
        FOREIGN KEY (channel_id) REFERENCES channels (id),
    CONSTRAINT fk_channel_members_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE private_chats (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    user1_id BIGINT NOT NULL,
    user2_id BIGINT NOT NULL,
    initiator_id BIGINT NOT NULL,
    status ENUM('ACTIVE', 'DELETED', 'PENDING', 'REJECTED') NOT NULL,
    CONSTRAINT pk_private_chats PRIMARY KEY (id),
    CONSTRAINT uk_private_chats_users UNIQUE (user1_id, user2_id),
    CONSTRAINT fk_private_chats_user1
        FOREIGN KEY (user1_id) REFERENCES users (id),
    CONSTRAINT fk_private_chats_user2
        FOREIGN KEY (user2_id) REFERENCES users (id),
    CONSTRAINT fk_private_chats_initiator
        FOREIGN KEY (initiator_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    file_name VARCHAR(255) NULL,
    file_path VARCHAR(500) NULL,
    is_recalled BIT(1) NOT NULL,
    type ENUM('FILE', 'IMAGE', 'SYSTEM', 'TEXT') NOT NULL,
    channel_id BIGINT NULL,
    sender_id BIGINT NOT NULL,
    private_chat_id BIGINT NULL,
    CONSTRAINT pk_messages PRIMARY KEY (id),
    CONSTRAINT fk_messages_channel
        FOREIGN KEY (channel_id) REFERENCES channels (id),
    CONSTRAINT fk_messages_sender
        FOREIGN KEY (sender_id) REFERENCES users (id),
    CONSTRAINT fk_messages_private_chat
        FOREIGN KEY (private_chat_id) REFERENCES private_chats (id),
    INDEX idx_channel_created (channel_id, created_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE message_reads (
    id BIGINT NOT NULL AUTO_INCREMENT,
    read_at DATETIME(6) NOT NULL,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT pk_message_reads PRIMARY KEY (id),
    CONSTRAINT uk_message_reads_message_user UNIQUE (message_id, user_id),
    CONSTRAINT fk_message_reads_message
        FOREIGN KEY (message_id) REFERENCES messages (id),
    CONSTRAINT fk_message_reads_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

