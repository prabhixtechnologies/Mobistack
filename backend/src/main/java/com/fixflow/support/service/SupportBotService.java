package com.fixflow.support.service;

import com.fixflow.config.FixFlowProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportBotService {

    public record BotReply(String body, boolean escalate) {
    }

    private final FixFlowProperties properties;

    public BotReply reply(String rawMessage, String userName) {
        String text = rawMessage == null ? "" : rawMessage.trim();
        if (text.isBlank()) {
            return new BotReply("Tell me what you need — search, stock, a sale, a repair, or a person from Prabhix.", false);
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (wantsHuman(lower)) {
            return new BotReply(
                    "I am putting you through to Prabhix support. Someone will reply in this thread. "
                            + "You can also write " + properties.getPlatform().getSupportEmail() + ".",
                    true);
        }

        String canned = canned(lower, userName == null ? "there" : userName);
        if (canned != null) {
            return new BotReply(canned, false);
        }

        String generated = generate(text);
        if (generated != null && !generated.isBlank()) {
            return new BotReply(generated, false);
        }

        return new BotReply(
                "I can help with search, stock, sales, repairs, devices, sign-in, and updates. "
                        + "Say “talk to a person” if you want Prabhix support on this thread.",
                false);
    }

    static boolean wantsHuman(String lower) {
        return lower.contains("talk to a person")
                || lower.contains("human")
                || lower.contains("agent")
                || lower.contains("escalate")
                || lower.contains("real person")
                || lower.contains("contact support")
                || lower.contains("call support");
    }

    private String canned(String lower, String name) {
        if (lower.matches("^(hi|hello|hey|yo)\\b.*") || lower.equals("help")) {
            return "Hi " + name + ". I am the MobiStack assistant. Ask about selling a part, finding stock, "
                    + "opening a repair, device limits, or updates. Say “talk to a person” for Prabhix support.";
        }
        if (containsAny(lower, "search", "find phone", "realme", "device")) {
            return "Type the phone in Search — aliases and barcodes work. Open the device to see which parts fit, "
                    + "live stock, and the price you can charge. Compatibility is per part category, not per phone.";
        }
        if (containsAny(lower, "stock", "inventory", "barcode")) {
            return "Inventory is a ledger. Receive stock on Purchases, sell it on Sales, or consume it on a repair. "
                    + "The counter app can scan barcodes and keep working offline, then sync.";
        }
        if (containsAny(lower, "sale", "sell", "invoice", "bill")) {
            return "Open Sales, pick the customer or walk-in, add the variant, and complete. "
                    + "The invoice prints from the sale. A void puts the stock back.";
        }
        if (containsAny(lower, "repair", "job", "ticket")) {
            return "Open Repairs, create a job, then add parts. Each part posts an inventory movement. "
                    + "Move the job through diagnosing → in repair → ready → delivered.";
        }
        if (containsAny(lower, "login", "password", "otp", "whatsapp", "magic")) {
            return "You can sign in with password, magic link, email code, SMS, or WhatsApp. "
                    + "Google works when it is configured. Reset instructions go to your email.";
        }
        if (containsAny(lower, "device limit", "too many device", "signed in", "tablet", "session")) {
            return "Each account has a device cap (default 3). Signing in on a new phone drops the oldest session "
                    + "unless the shop is set to reject extras. Owners can change the cap in Settings. "
                    + "Platform admins can revoke any session.";
        }
        if (containsAny(lower, "update", "ota", "apk", "ipa", "version")) {
            return "JavaScript fixes go out over OTA on the production channel. When the native build is too old, "
                    + "the app asks you to install the full Android or iOS build from "
                    + properties.getPlatform().getPublicOrigin() + "/app.";
        }
        if (containsAny(lower, "https", "domain", "website", "url", "host")) {
            return "The live console is " + properties.getPlatform().getPublicOrigin()
                    + " over HTTPS. Point the app at that origin with EXPO_PUBLIC_API_URL.";
        }
        if (containsAny(lower, "price", "wholesale", "repair price")) {
            return "Each variant has cost, retail, wholesale, repair, and a floor. The engine never goes below min price.";
        }
        return null;
    }

    private String generate(String message) {
        String key = properties.getChat().getOpenaiApiKey();
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", properties.getChat().getOpenaiModel(),
                    "temperature", 0.2,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt()),
                            Map.of("role", "user", "content", message)));
            Map<?, ?> response = RestClient.create()
                    .post()
                    .uri("https://api.openai.com/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + key)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                return null;
            }
            Object choices = response.get("choices");
            if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
                Object messageNode = first.get("message");
                if (messageNode instanceof Map<?, ?> msg && msg.get("content") instanceof String content) {
                    return content.trim();
                }
            }
        } catch (Exception ex) {
            log.warn("Chat model unavailable: {}", ex.getMessage());
        }
        return null;
    }

    private String systemPrompt() {
        return """
                You are the MobiStack assistant for Prabhix Technologies Pvt Ltd.
                Product site: %s
                Help shop staff with inventory, compatibility, sales, repairs, sign-in, devices, and updates.
                Compatibility is per part category, not per phone. Stock is a ledger.
                Never invent another tenant table or a second login system.
                If the user wants a human, tell them to say “talk to a person”.
                Keep answers short.
                """.formatted(properties.getPlatform().getPublicOrigin());
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
