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

    // 本接口没有实现类：Spring Data 在启动时为它生成代理，并把方法名解析成查询。
    // 方法名里写的是 Java 字段名（isPublic、name），与 @JoinColumn(name=...) 要求的
    // 数据库列名规则相反；名字写错会在启动时报 "No property xxx found"。

    Page<Channel> findByIsPublicTrueOrderByCreatedAtDesc(Pageable pageable);
    Page<Channel> findByIsPublicTrueAndNameContainingIgnoreCase(String keyword, Pageable pageable);
    Optional<Channel> findByInviteCode(String inviteCode);
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);

    // 悲观写锁：读-改-写（转让、解散、踢人）前先锁住这一行，防止并发覆盖。
    // 关键前提：必须在已开启的事务内调用。没有事务时语句结束即自动提交并释放锁，
    // 锁等于不存在。事务边界在调用方（ChannelViewService），本接口不加 @Transactional。
    // 另注：@Lock 的锁语义没有任何测试覆盖（测试里该方法被 Mockito stub 掉了）。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Channel c WHERE c.id = :id")
    Optional<Channel> findByIdForUpdate(@Param("id") Long id);
}
