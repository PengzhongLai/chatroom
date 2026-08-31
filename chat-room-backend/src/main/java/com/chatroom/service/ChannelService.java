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

    // Create channel
    @Transactional
    public Channel createChannel(String name, String description, boolean isPublic) {
        User creator = currentUser();
        String normalizedName = name.trim();
        if (channelRepository.existsByName(normalizedName)) {
            throw BusinessException.conflict("频道名称已存在");
        }
        Channel channel = new Channel();
        channel.setName(normalizedName);
        channel.setDescription(description == null ? null : description.trim());
        channel.setIsPublic(isPublic);
        channel.setCreator(creator);
        if (!isPublic) {
            channel.setInviteCode(UUID.randomUUID().toString().substring(0, 8));
        }
        channel = channelRepository.save(channel);

        ChannelMember member = new ChannelMember();
        member.setChannel(channel);
        member.setUser(creator);
        member.setRole(MemberRole.CREATOR);
        memberRepository.save(member);

        return channel;
    }

    // List public channels
    public Page<Channel> listChannels(String keyword, int page, int size) {
        PaginationPolicy.validate(page, size);
        if (keyword != null && !keyword.isBlank()) {
            return channelRepository.findByIsPublicTrueAndNameContainingIgnoreCase(
                    keyword.trim(), PageRequest.of(page, size)
            );
        }
        return channelRepository.findByIsPublicTrueOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    // Get channel detail
    public Channel getChannel(Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        ensureMember(channel);
        return channel;
    }

    // Update channel
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

    // Delete channel
    @Transactional
    public void deleteChannel(Long channelId) {
        Channel channel = channelRepository.findByIdForUpdate(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        if (!isCreator(channel, currentUser())) {
            throw BusinessException.forbidden("只有创建者才能解散频道");
        }
        // Delete message reads for messages in this channel
        List<Message> messages = messageRepository.findByChannel(channel);
        for (Message msg : messages) {
            messageReadRepository.deleteByMessage(msg);
        }
        // Delete messages
        messageRepository.deleteByChannel(channel);
        // Delete members
        memberRepository.deleteByChannel(channel);
        // Delete channel
        channelRepository.delete(channel);
    }

    // Join public channel (idempotent — returns existing membership if already joined)
    @Transactional
    public ChannelMember joinChannel(Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        User user = currentUser();

        if (!channel.getIsPublic()) {
            throw BusinessException.forbidden("私有频道需要邀请码");
        }

        // Already a member — return existing membership silently
        Optional<ChannelMember> existing = memberRepository.findByChannelAndUser(channel, user);
        if (existing.isPresent()) {
            return existing.get();
        }

        ChannelMember member = addMember(channel, user, HistoryLevel.ALL, null);
        sendSystemMessage(channel, user.getNickname() + " 加入了频道");
        return member;
    }

    // Join via invite code
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

    // Leave channel
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

    // Invite member (admin only, with history level)
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

    // Toggle mute
    @Transactional
    public Channel toggleMute(Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        ensureAdmin(channel);
        channel.setIsMuted(!channel.getIsMuted());
        channel = channelRepository.save(channel);

        // Broadcast channel update event
        Map<String, Object> updateEvent = new LinkedHashMap<>();
        updateEvent.put("type", "CHANNEL_UPDATE");
        updateEvent.put("channelId", channelId);
        updateEvent.put("isMuted", channel.getIsMuted());
        messagingTemplate.convertAndSend("/topic/channel." + channelId, updateEvent);

        // Broadcast system message
        String msg = channel.getIsMuted() ? "频道已被管理员禁言" : "频道已解除禁言";
        sendSystemMessage(channel, msg);

        return channel;
    }

    // List members
    public List<ChannelMember> listMembers(Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> BusinessException.notFound("频道不存在"));
        ensureMember(channel);
        return memberRepository.findByChannel(channel);
    }

    // Update member. Role changes must use the dedicated creator-only endpoints.
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

    // Transfer channel ownership (creator only)
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

    // Promote member to admin (creator only)
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

    // Demote admin to member (creator only)
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

    // My channels
    public List<ChannelMember> myChannels() {
        return memberRepository.findByUser(currentUser());
    }

    // Helpers
    private ChannelMember addMember(Channel channel, User user, HistoryLevel level, Integer limit) {
        ChannelMember member = new ChannelMember();
        member.setChannel(channel);
        member.setUser(user);
        member.setRole(MemberRole.MEMBER);
        member.setHistoryLevel(level);
        member.setHistoryLimit(limit);
        return memberRepository.save(member);
    }

    private void ensureMember(Channel channel) {
        User user = currentUser();
        if (!memberRepository.existsByChannelAndUser(channel, user)) {
            throw BusinessException.forbidden("你不是该频道的成员");
        }
    }

    private void ensureAdmin(Channel channel) {
        User user = currentUser();
        ChannelMember member = memberRepository.findByChannelAndUser(channel, user)
                .orElseThrow(() -> BusinessException.forbidden("你不是该频道的成员"));
        if (!isCreator(channel, user) && member.getRole() != MemberRole.ADMIN) {
            throw BusinessException.forbidden("需要管理员权限");
        }
    }

    private void ensureCreator(Channel channel) {
        User user = currentUser();
        if (!isCreator(channel, user)) {
            throw BusinessException.forbidden("只有创建者才能执行此操作");
        }
    }

    private boolean isCreator(Channel channel, User user) {
        return channel.getCreator() != null
                && channel.getCreator().getId().equals(user.getId());
    }

    private void assertSingleCreatorMirror(Channel channel, User authoritativeCreator) {
        List<ChannelMember> creatorMembers = memberRepository.findByChannel(channel).stream()
                .filter(member -> member.getRole() == MemberRole.CREATOR)
                .toList();
        if (creatorMembers.size() != 1
                || !creatorMembers.get(0).getUser().getId().equals(authoritativeCreator.getId())) {
            throw BusinessException.conflict("频道所有权数据不一致，请先修复创建者角色");
        }
    }

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
