package com.chatroom.repository;

import com.chatroom.entity.Channel;
import com.chatroom.entity.Message;
import com.chatroom.entity.PrivateChat;
import com.chatroom.repository.projection.SearchMessageProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    // Channel messages
    List<Message> findByChannel(Channel channel);
    Page<Message> findByChannelOrderByCreatedAtDesc(Channel channel, Pageable pageable);
    Page<Message> findByChannelAndCreatedAtAfterOrderByCreatedAtDesc(Channel channel, LocalDateTime after, Pageable pageable);
    void deleteByChannel(Channel channel);

    // Private chat messages
    Page<Message> findByPrivateChatOrderByCreatedAtDesc(PrivateChat privateChat, Pageable pageable);
    long countByPrivateChatAndSenderId(PrivateChat privateChat, Long senderId);
    void deleteByPrivateChat(PrivateChat privateChat);

    @Query(
            value = """
                    SELECT
                        m.id AS id,
                        m.channel_id AS channelId,
                        m.private_chat_id AS chatId,
                        sender.id AS senderId,
                        sender.username AS senderUsername,
                        sender.nickname AS senderNickname,
                        m.type AS type,
                        m.content AS content,
                        m.file_name AS fileName,
                        m.file_path AS filePath,
                        m.is_recalled AS isRecalled,
                        m.created_at AS createdAt,
                        CASE WHEN m.channel_id IS NOT NULL THEN 'channel' ELSE 'private' END AS context,
                        COALESCE(m.channel_id, m.private_chat_id) AS contextId,
                        CASE
                            WHEN m.channel_id IS NOT NULL THEN c.name
                            WHEN pc.user1_id = :userId THEN user2.nickname
                            ELSE user1.nickname
                        END AS contextName
                    FROM messages m
                    JOIN users sender ON sender.id = m.sender_id
                    LEFT JOIN channels c ON c.id = m.channel_id
                    LEFT JOIN channel_members cm
                           ON cm.channel_id = m.channel_id
                          AND cm.user_id = :userId
                    LEFT JOIN private_chats pc ON pc.id = m.private_chat_id
                    LEFT JOIN users user1 ON user1.id = pc.user1_id
                    LEFT JOIN users user2 ON user2.id = pc.user2_id
                    WHERE m.is_recalled = FALSE
                      AND (
                            LOWER(COALESCE(m.content, '')) LIKE CONCAT('%', LOWER(:keyword), '%') ESCAPE '!'
                         OR LOWER(COALESCE(m.file_name, '')) LIKE CONCAT('%', LOWER(:keyword), '%') ESCAPE '!'
                      )
                      AND (
                            (
                                m.channel_id IS NOT NULL
                                AND cm.id IS NOT NULL
                                AND (
                                       cm.history_level = 'ALL'
                                    OR (cm.history_level = 'NONE' AND m.created_at >= cm.joined_at)
                                    OR (
                                        cm.history_level = 'LIMITED'
                                        AND (
                                            SELECT COUNT(*)
                                            FROM messages newer
                                            WHERE newer.channel_id = m.channel_id
                                              AND (
                                                    newer.created_at > m.created_at
                                                 OR (newer.created_at = m.created_at AND newer.id > m.id)
                                              )
                                        ) < COALESCE(cm.history_limit, 50)
                                    )
                                )
                            )
                         OR (
                                m.private_chat_id IS NOT NULL
                                AND pc.status = 'ACTIVE'
                                AND (pc.user1_id = :userId OR pc.user2_id = :userId)
                            )
                      )
                    ORDER BY m.created_at DESC, m.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM messages m
                    LEFT JOIN channel_members cm
                           ON cm.channel_id = m.channel_id
                          AND cm.user_id = :userId
                    LEFT JOIN private_chats pc ON pc.id = m.private_chat_id
                    WHERE m.is_recalled = FALSE
                      AND (
                            LOWER(COALESCE(m.content, '')) LIKE CONCAT('%', LOWER(:keyword), '%') ESCAPE '!'
                         OR LOWER(COALESCE(m.file_name, '')) LIKE CONCAT('%', LOWER(:keyword), '%') ESCAPE '!'
                      )
                      AND (
                            (
                                m.channel_id IS NOT NULL
                                AND cm.id IS NOT NULL
                                AND (
                                       cm.history_level = 'ALL'
                                    OR (cm.history_level = 'NONE' AND m.created_at >= cm.joined_at)
                                    OR (
                                        cm.history_level = 'LIMITED'
                                        AND (
                                            SELECT COUNT(*)
                                            FROM messages newer
                                            WHERE newer.channel_id = m.channel_id
                                              AND (
                                                    newer.created_at > m.created_at
                                                 OR (newer.created_at = m.created_at AND newer.id > m.id)
                                              )
                                        ) < COALESCE(cm.history_limit, 50)
                                    )
                                )
                            )
                         OR (
                                m.private_chat_id IS NOT NULL
                                AND pc.status = 'ACTIVE'
                                AND (pc.user1_id = :userId OR pc.user2_id = :userId)
                            )
                      )
                    """,
            nativeQuery = true
    )
    Page<SearchMessageProjection> searchAccessible(
            @Param("userId") Long userId,
            @Param("keyword") String escapedKeyword,
            Pageable pageable
    );
}
