package com.chatroom.service;

import com.chatroom.entity.Channel;
import com.chatroom.entity.ChannelMember;
import com.chatroom.entity.User;
import com.chatroom.enums.HistoryLevel;
import com.chatroom.enums.MemberRole;
import com.chatroom.enums.MessageType;
import com.chatroom.entity.Message;
import com.chatroom.exception.BusinessException;
import com.chatroom.repository.ChannelMemberRepository;
import com.chatroom.repository.ChannelRepository;
import com.chatroom.repository.UserRepository;
import com.chatroom.repository.MessageRepository;
import com.chatroom.repository.MessageReadRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chatroom.validation.PaginationPolicy;

import java.util.*;
import java.util.List;

/**
 * 频道的业务规则中心：创建、修改、解散、加入退出、邀请、角色管理、禁言、所有权转让。
 * 也负责在解散、禁言、转让等操作后通过 WebSocket 广播通知。
 */
@Service
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final MessageReadRepository messageReadRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChannelService(ChannelRepository channelRepository,
                          ChannelMemberRepository memberRepository,
                          UserRepository userRepository,
                          MessageRepository messageRepository,
                          MessageReadRepository messageReadRepository,
                          SimpMessagingTemplate messagingTemplate) {
        this.channelRepository = channelRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.messageReadRepository = messageReadRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /** 取得当前登录用户。身份来自服务端认证上下文，未登录或用户已不存在时抛未授权异常 */
    private User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof Long userId)) {
            throw BusinessException.unauthorized("用户未登录");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.unauthorized("登录用户不存在"));
    }

    /** 创建频道，并把创建者本人写为该频道的创建者成员 */
    // 需要写两张表：channels 记创建者，channel_members 记创建者在这个群里。
    // 两者必须在同一事务内，顺序也不能反：channel_members.channel_id 是非空外键，
    // 必须等 channels 保存并回填自增 ID 之后，才能建立成员记录。
    @Transactional
    public Channel createChannel(String name, String description, boolean isPublic) {
        User creator = currentUser();                     // 创建者取自登录身份
        String normalizedName = name.trim();              // 先去掉首尾空格再判重
        if (channelRepository.existsByName(normalizedName)) {
            throw BusinessException.conflict("频道名称已存在");
        }
        Channel channel = new Channel();
        channel.setName(normalizedName);
        channel.setDescription(description == null ? null : description.trim());
        channel.setIsPublic(isPublic);
        channel.setCreator(creator);
        if (!isPublic) {
            // 只有私密频道才生成邀请码，取 UUID 的前 8 位
            channel.setInviteCode(UUID.randomUUID().toString().substring(0, 8));
        }
        channel = channelRepository.save(channel);        // 保存后 channel 才拿到自增 ID

        ChannelMember member = new ChannelMember();
        member.setChannel(channel);
        member.setUser(creator);
        member.setRole(MemberRole.CREATOR);               // 创建者本人的角色是 CREATOR
        memberRepository.save(member);

        return channel;
    }

    /** 分页查询公开频道，带关键词时按名称模糊匹配 */
    public Page<Channel> listChannels(String keyword, int page, int size) {
        PaginationPolicy.validate(page, size);
        if (keyword != null && !keyword.isBlank()) {
            return channelRepository.findByIsPublicTrueAndNameContainingIgnoreCase(
                    keyword.trim(), PageRequest.of(page, size)
            );
        }
        return channelRepository.findByIsPublicTrueOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    /** 查询频道，并校验当前用户是该频道成员 */
    public Channel getChannel(Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        ensureMember(channel);
        return channel;
    }

    /** 修改频道名称或描述。仅管理员和创建者可操作，改名前会检查是否与其他频道重名 */
    @Transactional
    public Channel updateChannel(Long channelId, String name, String description) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        ensureAdmin(channel);
        if (name != null) {
            String normalizedName = name.trim();
            if (channelRepository.existsByNameAndIdNot(normalizedName, channelId)) {
                throw BusinessException.conflict("频道名称已存在");
            }
            channel.setName(normalizedName);
        }
        if (description != null) channel.setDescription(description.trim());
        return channelRepository.save(channel);
    }

    /** 解散频道。仅创建者可操作，会依次清除该频道的已读记录、消息、成员，最后删频道本身 */
    @Transactional
    public void deleteChannel(Long channelId) {
        Channel channel = channelRepository.findByIdForUpdate(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        if (!isCreator(channel, currentUser())) {
            throw BusinessException.forbidden("只有创建者才能解散频道");
        }
        // 已读记录引用了消息，必须先于消息删除
        List<Message> messages = messageRepository.findByChannel(channel);
        for (Message msg : messages) {
            messageReadRepository.deleteByMessage(msg);
        }
        messageRepository.deleteByChannel(channel);   // 删除该频道的全部消息
        memberRepository.deleteByChannel(channel);    // 删除全部成员记录
        channelRepository.delete(channel);            // 最后删除频道本身
    }

    /** 加入公开频道，并广播一条"xx 加入了频道"的系统消息。已是成员时直接返回原记录 */
    @Transactional
    public ChannelMember joinChannel(Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        User user = currentUser();

        if (!channel.getIsPublic()) {
            throw BusinessException.forbidden("私有频道需要邀请码");
        }

        Optional<ChannelMember> existing = memberRepository.findByChannelAndUser(channel, user);
        if (existing.isPresent()) {
            return existing.get();       // 重复加入不报错，直接返回已有成员记录
        }

        ChannelMember member = addMember(channel, user, HistoryLevel.ALL, null);
        sendSystemMessage(channel, user.getNickname() + " 加入了频道");
        return member;
    }

    /** 凭邀请码加入频道，并广播系统消息。已经是成员时报错 */
    @Transactional
    public ChannelMember joinByInviteCode(String inviteCode) {
        Channel channel = channelRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> BusinessException.badRequest("邀请码无效"));
        User user = currentUser();
        if (memberRepository.existsByChannelAndUser(channel, user)) {
            throw BusinessException.conflict("你已经是频道成员");
        }
        ChannelMember member = addMember(channel, user, HistoryLevel.ALL, null);
        sendSystemMessage(channel, user.getNickname() + " 加入了频道");
        return member;
    }

    /** 退出频道并广播系统消息。创建者不能退出，必须先转让或解散 */
    @Transactional
    public void leaveChannel(Long channelId) {
        Channel channel = channelRepository.findByIdForUpdate(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        User user = currentUser();
        ChannelMember member = memberRepository.findByChannelAndUser(channel, user)
                .orElseThrow(() -> BusinessException.forbidden("你不是该频道的成员"));
        if (isCreator(channel, user)) {
            throw BusinessException.conflict("创建者不能退出，请先解散频道或转让");
        }
        memberRepository.delete(member);
        sendSystemMessage(channel, user.getNickname() + " 离开了频道");
    }

    /** 把指定用户加入频道，可指定其可查看的历史范围。仅管理员和创建者可操作 */
    @Transactional
    public ChannelMember inviteMember(Long channelId, Long userId, HistoryLevel historyLevel, Integer historyLimit) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        ensureAdmin(channel);
        User target = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        if (memberRepository.existsByChannelAndUser(channel, target)) {
            throw BusinessException.conflict("该用户已是频道成员");
        }
        return addMember(channel, target, historyLevel, historyLimit);
    }

    /** 切换全员禁言开关：开启后普通成员无法发言，管理员和创建者不受限制 */
    @Transactional
    public Channel toggleMute(Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        ensureAdmin(channel);
        channel.setIsMuted(!channel.getIsMuted());
        channel = channelRepository.save(channel);

        // 先广播频道状态变更事件，让前端更新禁言标识
        Map<String, Object> updateEvent = new LinkedHashMap<>();
        updateEvent.put("type", "CHANNEL_UPDATE");
        updateEvent.put("channelId", channelId);
        updateEvent.put("isMuted", channel.getIsMuted());
        messagingTemplate.convertAndSend("/topic/channel." + channelId, updateEvent);

        // 再广播一条系统消息，在聊天记录里留痕
        String msg = channel.getIsMuted() ? "频道已被管理员禁言" : "频道已解除禁言";
        sendSystemMessage(channel, msg);

        return channel;
    }

    /** 查询频道成员列表，仅频道成员可查看 */
    public List<ChannelMember> listMembers(Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        ensureMember(channel);
        return memberRepository.findByChannel(channel);
    }

    /**
     * 对成员执行操作，目前只支持踢出（action 传 kick）。
     * 管理员只能踢普通成员，创建者可以踢管理员。
     */
    @Transactional
    public void updateMember(Long channelId, Long userId, String action) {
        Channel channel = channelRepository.findByIdForUpdate(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        User actor = currentUser();
        ChannelMember actorMember = memberRepository.findByChannelAndUser(channel, actor)
                .orElseThrow(() -> BusinessException.forbidden("你不是该频道的成员"));
        boolean actorIsCreator = isCreator(channel, actor);
        if (!actorIsCreator && actorMember.getRole() != MemberRole.ADMIN) {
            throw BusinessException.forbidden("需要管理员权限");
        }

        if (!"kick".equals(action)) {
            throw BusinessException.badRequest("通用成员接口仅支持踢出操作");
        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        ChannelMember member = memberRepository.findByChannelAndUser(channel, target)
                .orElseThrow(() -> BusinessException.notFound("该用户不是频道成员"));

        if (isCreator(channel, target)) {
            throw BusinessException.forbidden("不能踢出创建者");
        }
        if (member.getRole() == MemberRole.CREATOR) {
            throw BusinessException.conflict("频道角色数据不一致，请先修复所有权");
        }
        if (!actorIsCreator && member.getRole() != MemberRole.MEMBER) {
            throw BusinessException.forbidden("管理员只能踢出普通成员");
        }

        memberRepository.delete(member);
        sendSystemMessage(channel, target.getNickname() + " 被移出了频道");
    }

    /**
     * 转让频道所有权。仅创建者可操作。
     * 同时更新 channels.creator_id（权威记录）和两条成员记录的角色，
     * 原创建者降为管理员，新创建者升为 CREATOR。
     */
    @Transactional
    public void transferOwnership(Long channelId, Long targetUserId) {
        Channel channel = channelRepository.findByIdForUpdate(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        User currentUser = currentUser();
        if (!isCreator(channel, currentUser)) {
            throw BusinessException.forbidden("只有创建者才能转让频道");
        }
        if (currentUser.getId().equals(targetUserId)) {
            throw BusinessException.badRequest("不能将频道转让给自己");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));

        ChannelMember myMember = memberRepository.findByChannelAndUser(channel, currentUser)
                .orElseThrow(() -> BusinessException.conflict("频道创建者成员记录不存在"));
        if (myMember.getRole() != MemberRole.CREATOR) {
            throw BusinessException.conflict("频道所有权数据不一致，请先修复创建者角色");
        }

        ChannelMember targetMember = memberRepository.findByChannelAndUser(channel, targetUser)
                .orElseThrow(() -> BusinessException.notFound("目标用户不是频道成员"));

        assertSingleCreatorMirror(channel, currentUser);

        channel.setCreator(targetUser);
        myMember.setRole(MemberRole.ADMIN);
        targetMember.setRole(MemberRole.CREATOR);
        channelRepository.save(channel);
        memberRepository.save(myMember);
        memberRepository.save(targetMember);

        sendSystemMessage(channel, "频道已转让给 " + targetUser.getNickname());
    }

    /** 把普通成员提升为管理员。仅创建者可操作 */
    @Transactional
    public void promoteToAdmin(Long channelId, Long targetUserId) {
        Channel channel = channelRepository.findByIdForUpdate(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        ensureCreator(channel);

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));

        ChannelMember targetMember = memberRepository.findByChannelAndUser(channel, targetUser)
                .orElseThrow(() -> BusinessException.notFound("目标用户不是频道成员"));
        if (targetMember.getRole() != MemberRole.MEMBER) {
            throw BusinessException.conflict("只能提升普通成员");
        }

        targetMember.setRole(MemberRole.ADMIN);
        memberRepository.save(targetMember);
    }

    /** 把管理员降为普通成员。仅创建者可操作 */
    @Transactional
    public void demoteToMember(Long channelId, Long targetUserId) {
        Channel channel = channelRepository.findByIdForUpdate(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        ensureCreator(channel);

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));

        ChannelMember targetMember = memberRepository.findByChannelAndUser(channel, targetUser)
                .orElseThrow(() -> BusinessException.notFound("目标用户不是频道成员"));
        if (targetMember.getRole() != MemberRole.ADMIN) {
            throw BusinessException.conflict("只能降级管理员");
        }

        targetMember.setRole(MemberRole.MEMBER);
        memberRepository.save(targetMember);
    }

    /** 查询当前用户加入的全部频道，以成员记录的形式返回 */
    public List<ChannelMember> myChannels() {
        return memberRepository.findByUser(currentUser());
    }

    // Helpers

    /** 新增一条成员记录，默认角色为普通成员 */
    private ChannelMember addMember(Channel channel, User user, HistoryLevel level, Integer limit) {
        ChannelMember member = new ChannelMember();
        member.setChannel(channel);
        member.setUser(user);
        member.setRole(MemberRole.MEMBER);
        member.setHistoryLevel(level);
        member.setHistoryLimit(limit);
        return memberRepository.save(member);
    }

    /** 校验当前用户是频道成员，否则抛异常 */
    private void ensureMember(Channel channel) {
        User user = currentUser();
        if (!memberRepository.existsByChannelAndUser(channel, user)) {
            throw BusinessException.forbidden("你不是该频道的成员");
        }
    }

    /** 校验当前用户是创建者或管理员，否则抛异常 */
    private void ensureAdmin(Channel channel) {
        User user = currentUser();
        ChannelMember member = memberRepository.findByChannelAndUser(channel, user)
                .orElseThrow(() -> BusinessException.forbidden("你不是该频道的成员"));
        if (!isCreator(channel, user) && member.getRole() != MemberRole.ADMIN) {
            throw BusinessException.forbidden("需要管理员权限");
        }
    }

    /** 校验当前用户是频道创建者，否则抛异常 */
    private void ensureCreator(Channel channel) {
        User user = currentUser();
        if (!isCreator(channel, user)) {
            throw BusinessException.forbidden("只有创建者才能执行此操作");
        }
    }

    /** 判断某用户是否为频道创建者。以 channels.creator_id 为准，不看成员角色 */
    private boolean isCreator(Channel channel, User user) {
        return channel.getCreator() != null
                && channel.getCreator().getId().equals(user.getId());
    }

    /**
     * 校验成员表里"CREATOR 角色"有且只有一个，且正是权威创建者。
     * 转让前调用，避免在数据已经不一致的情况下继续修改。
     */
    private void assertSingleCreatorMirror(Channel channel, User authoritativeCreator) {
        List<ChannelMember> creatorMembers = memberRepository.findByChannel(channel).stream()
                .filter(member -> member.getRole() == MemberRole.CREATOR)
                .toList();
        if (creatorMembers.size() != 1
                || !creatorMembers.get(0).getUser().getId().equals(authoritativeCreator.getId())) {
            throw BusinessException.conflict("频道所有权数据不一致，请先修复创建者角色");
        }
    }

    /** 发送一条系统消息到频道，只广播不入库，id 和 sender 为 null */
    private void sendSystemMessage(Channel channel, String content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", null);
        payload.put("channelId", channel.getId());
        payload.put("sender", null);
        payload.put("type", "SYSTEM");
        payload.put("content", content);
        payload.put("fileName", null);
        payload.put("filePath", null);
        payload.put("isRecalled", false);
        payload.put("createdAt", java.time.LocalDateTime.now().toString());
        messagingTemplate.convertAndSend("/topic/channel." + channel.getId(), payload);
    }
}
