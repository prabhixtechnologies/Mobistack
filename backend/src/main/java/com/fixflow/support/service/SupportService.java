package com.fixflow.support.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.notify.NotificationService;
import com.fixflow.security.CurrentUser;
import com.fixflow.support.domain.SupportConversation;
import com.fixflow.support.domain.SupportMessage;
import com.fixflow.support.repository.SupportConversationRepository;
import com.fixflow.support.repository.SupportMessageRepository;
import com.fixflow.user.domain.User;
import com.fixflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupportService {

    public record MessageCard(UUID id, String authorType, String body, Instant createdAt) {
    }

    public record ConversationCard(
            UUID id,
            String subject,
            String status,
            String channel,
            UUID shopId,
            UUID userId,
            String userName,
            Instant lastMessageAt,
            List<MessageCard> messages
    ) {
    }

    private final SupportConversationRepository conversationRepository;
    private final SupportMessageRepository messageRepository;
    private final SupportBotService botService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public ConversationCard chat(String message, String channel) {
        UUID userId = CurrentUser.userId();
        UUID shopId = CurrentUser.find().map(principal -> principal.getShopId()).orElse(null);
        String name = CurrentUser.require().getFullName();
        SupportConversation open = conversationRepository.findByUserIdOrderByLastMessageAtDesc(userId).stream()
                .filter(row -> !SupportConversation.RESOLVED.equals(row.getStatus()))
                .findFirst()
                .orElseGet(() -> start(userId, shopId, subjectFrom(message), channel));
        append(open, SupportMessage.USER, userId, message);
        SupportBotService.BotReply reply = botService.reply(message, name);
        append(open, SupportMessage.BOT, null, reply.body());
        if (reply.escalate()) {
            open.setStatus(SupportConversation.WAITING);
            conversationRepository.save(open);
            notifyAdmins(open, message);
        }
        return toCard(open, true);
    }

    @Transactional
    public ConversationCard openTicket(String subject, String message, String channel) {
        UUID userId = CurrentUser.userId();
        UUID shopId = CurrentUser.find().map(principal -> principal.getShopId()).orElse(null);
        SupportConversation conversation = start(userId, shopId,
                subject == null || subject.isBlank() ? subjectFrom(message) : subject.trim(), channel);
        if (message != null && !message.isBlank()) {
            append(conversation, SupportMessage.USER, userId, message);
            SupportBotService.BotReply reply = botService.reply(message, CurrentUser.require().getFullName());
            append(conversation, SupportMessage.BOT, null, reply.body());
            if (reply.escalate()) {
                conversation.setStatus(SupportConversation.WAITING);
            }
        }
        conversation.setStatus(SupportConversation.WAITING);
        conversationRepository.save(conversation);
        notifyAdmins(conversation, message);
        return toCard(conversation, true);
    }

    @Transactional(readOnly = true)
    public List<ConversationCard> mine() {
        return conversationRepository.findByUserIdOrderByLastMessageAtDesc(CurrentUser.userId()).stream()
                .map(row -> toCard(row, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationCard getMine(UUID id) {
        SupportConversation conversation = conversationRepository.findByIdAndUserId(id, CurrentUser.userId())
                .orElseThrow(() -> ApiException.notFound("Conversation", id));
        return toCard(conversation, true);
    }

    @Transactional
    public ConversationCard replyMine(UUID id, String body) {
        SupportConversation conversation = conversationRepository.findByIdAndUserId(id, CurrentUser.userId())
                .orElseThrow(() -> ApiException.notFound("Conversation", id));
        append(conversation, SupportMessage.USER, CurrentUser.userId(), body);
        SupportBotService.BotReply reply = botService.reply(body, CurrentUser.require().getFullName());
        append(conversation, SupportMessage.BOT, null, reply.body());
        if (reply.escalate() || SupportConversation.RESOLVED.equals(conversation.getStatus())) {
            conversation.setStatus(SupportConversation.WAITING);
            conversationRepository.save(conversation);
            notifyAdmins(conversation, body);
        }
        return toCard(conversation, true);
    }

    @Transactional(readOnly = true)
    public List<ConversationCard> adminList(String status) {
        var page = status == null || status.isBlank()
                ? conversationRepository.findAllByOrderByLastMessageAtDesc(PageRequest.of(0, 80))
                : conversationRepository.findByStatusOrderByLastMessageAtDesc(status.trim().toUpperCase(),
                PageRequest.of(0, 80));
        return page.getContent().stream().map(row -> toCard(row, true)).toList();
    }

    @Transactional
    public ConversationCard adminReply(UUID id, String body) {
        SupportConversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Conversation", id));
        append(conversation, SupportMessage.AGENT, CurrentUser.userId(), body);
        conversation.setStatus(SupportConversation.OPEN);
        conversation.setAssignedTo(CurrentUser.userId());
        conversationRepository.save(conversation);
        notificationService.emit(conversation.getShopId(), conversation.getUserId(), "SUPPORT_REPLY",
                null, "Prabhix support replied", body);
        return toCard(conversation, true);
    }

    @Transactional
    public ConversationCard resolve(UUID id) {
        SupportConversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Conversation", id));
        conversation.setStatus(SupportConversation.RESOLVED);
        conversationRepository.save(conversation);
        return toCard(conversation, false);
    }

    private SupportConversation start(UUID userId, UUID shopId, String subject, String channel) {
        SupportConversation conversation = new SupportConversation();
        conversation.setUserId(userId);
        conversation.setShopId(shopId);
        conversation.setSubject(subject);
        conversation.setChannel(channel == null || channel.isBlank() ? "WEB" : channel.trim().toUpperCase());
        conversation.setStatus(SupportConversation.OPEN);
        conversation.setLastMessageAt(Instant.now());
        return conversationRepository.save(conversation);
    }

    private void append(SupportConversation conversation, String authorType, UUID userId, String body) {
        SupportMessage message = new SupportMessage();
        message.setConversationId(conversation.getId());
        message.setAuthorType(authorType);
        message.setUserId(userId);
        message.setBody(body == null ? "" : body.trim());
        messageRepository.save(message);
        conversation.setLastMessageAt(Instant.now());
        conversationRepository.save(conversation);
    }

    private void notifyAdmins(SupportConversation conversation, String preview) {
        for (User admin : userRepository.findBySystemAdminTrueAndActiveTrue()) {
            notificationService.emit(conversation.getShopId(), admin.getId(), "SUPPORT_TICKET",
                    admin.getEmail(), "New support thread: " + conversation.getSubject(),
                    preview == null ? conversation.getSubject() : preview);
        }
    }

    private ConversationCard toCard(SupportConversation conversation, boolean includeMessages) {
        User user = userRepository.findById(conversation.getUserId()).orElse(null);
        List<MessageCard> messages = includeMessages
                ? messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
                .map(row -> new MessageCard(row.getId(), row.getAuthorType(), row.getBody(), row.getCreatedAt()))
                .toList()
                : List.of();
        return new ConversationCard(conversation.getId(), conversation.getSubject(), conversation.getStatus(),
                conversation.getChannel(), conversation.getShopId(), conversation.getUserId(),
                user == null ? null : user.getFullName(), conversation.getLastMessageAt(), messages);
    }

    private static String subjectFrom(String message) {
        if (message == null || message.isBlank()) {
            return "Support";
        }
        String trimmed = message.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80);
    }
}
