package com.chatroom.repository;

import com.chatroom.entity.PrivateChat;
import com.chatroom.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PrivateChatRepository extends JpaRepository<PrivateChat, Long> {

    /** Find existing chat between two users (order doesn't matter). */
    @Query("SELECT pc FROM PrivateChat pc WHERE " +
           "(pc.user1 = :userA AND pc.user2 = :userB) OR " +
           "(pc.user1 = :userB AND pc.user2 = :userA)")
    Optional<PrivateChat> findBetweenUsers(@Param("userA") User userA, @Param("userB") User userB);

    /** All chats for a user, ordered by most recent. */
    @Query("SELECT pc FROM PrivateChat pc WHERE pc.user1 = :user OR pc.user2 = :user ORDER BY pc.createdAt DESC")
    List<PrivateChat> findByUser(@Param("user") User user);
}
