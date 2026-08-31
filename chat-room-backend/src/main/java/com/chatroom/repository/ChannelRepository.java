package com.chatroom.repository;

import com.chatroom.entity.Channel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    Page<Channel> findByIsPublicTrueOrderByCreatedAtDesc(Pageable pageable);
    Page<Channel> findByIsPublicTrueAndNameContainingIgnoreCase(String keyword, Pageable pageable);
    Optional<Channel> findByInviteCode(String inviteCode);
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Channel c WHERE c.id = :id")
    Optional<Channel> findByIdForUpdate(@Param("id") Long id);
}
