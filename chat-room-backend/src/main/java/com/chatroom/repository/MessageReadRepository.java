package com.chatroom.repository;

import com.chatroom.entity.Message;
import com.chatroom.entity.MessageRead;
import com.chatroom.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MessageReadRepository extends JpaRepository<MessageRead, Long> {
    void deleteByMessage(Message message);
    Optional<MessageRead> findByMessageAndUser(Message message, User user);
}
