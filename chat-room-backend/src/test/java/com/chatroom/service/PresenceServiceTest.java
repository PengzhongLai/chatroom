package com.chatroom.service;

import com.chatroom.entity.User;
import com.chatroom.enums.UserStatus;
import com.chatroom.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock UserRepository userRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock SetOperations<String, String> setOperations;

    private PresenceService service() {
        return new PresenceService(stringRedisTemplate, userRepository, messagingTemplate);
    }

    private User user(Long id, UserStatus status) {
        User user = new User("u" + id, "p", "n" + id);
        user.setId(id);
        user.setStatus(status);
        return user;
    }

    @Test
    void userConnectedSetsOnlineWithTtlAndAddsToIndex() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, UserStatus.OFFLINE)));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        service().userConnected(1L);

        verify(valueOperations).set("presence:1", "ONLINE", 300L, TimeUnit.SECONDS);
        verify(setOperations).add("presence:online", "1");
        // 状态从 OFFLINE 变 ONLINE，需要持久化
        verify(userRepository).save(any(User.class));
        assertBroadcast(1L, "ONLINE");
    }

    @Test
    void userConnectedKeepsInvisibleAndDoesNotPersist() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, UserStatus.INVISIBLE)));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        service().userConnected(1L);

        verify(valueOperations).set("presence:1", "INVISIBLE", 300L, TimeUnit.SECONDS);
        // 隐身状态没变，不写数据库
        verify(userRepository, never()).save(any());
        // 隐身对外显示离线
        assertBroadcast(1L, "OFFLINE");
    }

    @Test
    void userConnectedUnknownUserIsNoOp() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        service().userConnected(99L);

        verifyNoInteractions(stringRedisTemplate, messagingTemplate);
    }

    @Test
    void userDisconnectedDeletesKeyAndBroadcastsOffline() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        service().userDisconnected(1L);

        verify(stringRedisTemplate).delete("presence:1");
        verify(setOperations).remove("presence:online", "1");
        assertBroadcast(1L, "OFFLINE");
    }

    @Test
    void setStatusPersistsAndWritesRedis() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, UserStatus.OFFLINE)));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        service().setStatus(1L, UserStatus.INVISIBLE);

        verify(userRepository).save(any(User.class));
        verify(valueOperations).set("presence:1", "INVISIBLE", 300L, TimeUnit.SECONDS);
        verify(setOperations).add("presence:online", "1");
        assertBroadcast(1L, "OFFLINE");
    }

    @Test
    void getAllStatusesMapsAndHidesInvisible() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("presence:online")).thenReturn(Set.of("1", "2"));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("presence:1")).thenReturn("ONLINE");
        when(valueOperations.get("presence:2")).thenReturn("INVISIBLE");

        Map<Long, String> result = service().getAllStatuses();

        assertThat(result).containsEntry(1L, "ONLINE").containsEntry(2L, "OFFLINE");
        verify(setOperations, never()).remove(eq("presence:online"), anyString());
    }

    @Test
    void getAllStatusesLazilyCleansExpiredMembers() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("presence:online")).thenReturn(Set.of("1", "2"));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("presence:1")).thenReturn("ONLINE");
        when(valueOperations.get("presence:2")).thenReturn(null);

        Map<Long, String> result = service().getAllStatuses();

        // 过期成员被排除并惰性清理
        assertThat(result).containsOnlyKeys(1L);
        verify(setOperations).remove("presence:online", "2");
    }

    @Test
    void getAllStatusesReturnsEmptyWhenNoMembers() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("presence:online")).thenReturn(Set.of());

        assertThat(service().getAllStatuses()).isEmpty();
    }

    @Test
    void renewPresenceRefreshesTtl() {
        when(stringRedisTemplate.expire("presence:1", 300L, TimeUnit.SECONDS)).thenReturn(true);

        service().renewPresence(1L);

        verify(stringRedisTemplate).expire("presence:1", 300L, TimeUnit.SECONDS);
        verifyNoInteractions(userRepository);
    }

    @Test
    void renewPresenceRestoresStatusWhenKeyExpired() {
        when(stringRedisTemplate.expire("presence:1", 300L, TimeUnit.SECONDS)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, UserStatus.INVISIBLE)));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        service().renewPresence(1L);

        // key 已过期 → 按 DB 持久化状态恢复（隐身保持隐身）
        verify(valueOperations).set("presence:1", "INVISIBLE", 300L, TimeUnit.SECONDS);
        verify(setOperations).add("presence:online", "1");
    }

    private void assertBroadcast(Long userId, String status) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/presence"), captor.capture());
        assertThat(captor.getValue())
                .containsEntry("userId", userId)
                .containsEntry("status", status);
    }
}
