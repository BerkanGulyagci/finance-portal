package com.finance.portal.assistant.infrastructure;

import com.finance.portal.assistant.application.AssistantProperties;
import com.finance.portal.assistant.application.model.ChatMessage;
import com.finance.portal.assistant.application.port.AssistantChatPort;
import com.finance.portal.assistant.application.tools.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Birincil: Groq. Groq kota/hata verirse (AssistantUnavailableException) otomatik Gemini fallback'ine düşer.
 * AssistantService bunu (@Primary) enjekte eder.
 */
@Component
@Primary
public class CompositeAssistantChatPort implements AssistantChatPort {

    private static final Logger log = LoggerFactory.getLogger(CompositeAssistantChatPort.class);

    private final GroqChatClient groq;
    private final GeminiChatClient gemini;
    private final AssistantProperties props;

    public CompositeAssistantChatPort(GroqChatClient groq, GeminiChatClient gemini, AssistantProperties props) {
        this.groq = groq;
        this.gemini = gemini;
        this.props = props;
    }

    @Override
    public String complete(List<ChatMessage> messages, ToolContext ctx) {
        List<String> chain = props.getChain();
        if (chain == null || chain.isEmpty()) {
            return legacy(messages, ctx);
        }

        // Model zinciri: "saglayici:model" adımlarını sırayla dene; kota/hata olunca sonrakine düş.
        AssistantUnavailableException last = null;
        for (String step : chain) {
            if (step == null || !step.contains(":")) {
                continue;
            }
            int idx = step.indexOf(':');
            String provider = step.substring(0, idx).trim().toLowerCase();
            String model = step.substring(idx + 1).trim();
            if (model.isEmpty()) {
                continue;
            }
            try {
                if ("groq".equals(provider) && props.isUsable()) {
                    return groq.completeWith(messages, ctx, model);
                }
                if ("gemini".equals(provider) && gemini.isUsable()) {
                    return gemini.completeWith(messages, ctx, model);
                }
                // sağlayıcı yapılandırılmamış (anahtar yok) → adımı atla
            } catch (AssistantUnavailableException e) {
                log.warn("Zincir adımı başarısız [{}] ({}), sonrakine geçiliyor", step, e.getMessage());
                last = e;
            }
        }
        if (last != null) {
            throw last; // mesajda "429" varsa AssistantService BUSY döndürür
        }
        throw new AssistantUnavailableException("No assistant provider configured (chain)");
    }

    /** Zincir tanımlı değilse eski davranış: Groq → Gemini. */
    private String legacy(List<ChatMessage> messages, ToolContext ctx) {
        if (props.isUsable()) {
            try {
                return groq.complete(messages, ctx);
            } catch (AssistantUnavailableException e) {
                if (gemini.isUsable()) {
                    log.warn("Groq başarısız ({}), Gemini fallback deneniyor", e.getMessage());
                    return gemini.complete(messages, ctx);
                }
                throw e;
            }
        }
        if (gemini.isUsable()) {
            return gemini.complete(messages, ctx);
        }
        throw new AssistantUnavailableException("No assistant provider configured");
    }
}
