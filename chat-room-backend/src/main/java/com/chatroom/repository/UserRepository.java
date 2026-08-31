package com.chatroom.repository;

import com.chatroom.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    List<User> findByUsernameContainingIgnoreCaseOrNicknameContainingIgnoreCase(
            String username,
            String nickname,
            Pageable pageable
    );
}
