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

/**
 * channels 表的数据库访问接口。方法名由 Spring Data 解析成查询语句，
 * 无需编写实现类。方法名里写的是 Java 字段名，不是数据库列名。
 */
public interface ChannelRepository extends JpaRepository<Channel, Long> {

    /** 分页查询所有公开频道，按创建时间倒序（最新的在前） */
    Page<Channel> findByIsPublicTrueOrderByCreatedAtDesc(Pageable pageable);

    /** 分页查询公开频道，按名称模糊匹配（忽略大小写） */
    Page<Channel> findByIsPublicTrueAndNameContainingIgnoreCase(String keyword, Pageable pageable);

    /** 按邀请码查询频道 */
    Optional<Channel> findByInviteCode(String inviteCode);

    /** 判断频道名是否已被使用 */
    boolean existsByName(String name);

    /** 判断除指定频道外，是否已有别的频道用了这个名称 */
    boolean existsByNameAndIdNot(String name, Long id);

    /**
     * 按 ID 查询频道并加悲观写锁（SELECT ... FOR UPDATE）。
     * 用于转让、解散、踢人等"读-判断-改"的操作，防止并发修改互相覆盖。
     * 必须在已开启的事务中调用，否则语句执行完就释放锁，等于没加锁。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Channel c WHERE c.id = :id")
    Optional<Channel> findByIdForUpdate(@Param("id") Long id);
}
