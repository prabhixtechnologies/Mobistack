package com.fixflow.support.web;

import com.fixflow.support.service.SupportService;
import com.fixflow.support.service.SupportService.ConversationCard;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
@Tag(name = "Support")
public class SupportController {

    public record ChatRequest(String message, String channel) {
    }

    public record TicketRequest(String subject, String message, String channel) {
    }

    private final SupportService supportService;

    @PostMapping("/chat")
    public ConversationCard chat(@RequestBody ChatRequest request) {
        return supportService.chat(request.message(), request.channel());
    }

    @PostMapping("/conversations")
    public ConversationCard open(@RequestBody TicketRequest request) {
        return supportService.openTicket(request.subject(), request.message(), request.channel());
    }

    @GetMapping("/conversations")
    public List<ConversationCard> mine() {
        return supportService.mine();
    }

    @GetMapping("/conversations/{id}")
    public ConversationCard one(@PathVariable UUID id) {
        return supportService.getMine(id);
    }

    @PostMapping("/conversations/{id}/messages")
    public ConversationCard reply(@PathVariable UUID id, @RequestBody ChatRequest request) {
        return supportService.replyMine(id, request.message());
    }
}
