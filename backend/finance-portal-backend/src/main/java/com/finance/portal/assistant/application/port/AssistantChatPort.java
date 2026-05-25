package com.finance.portal.assistant.application.port;

import com.finance.portal.assistant.application.model.ChatMessage;
import com.finance.portal.assistant.application.tools.ToolContext;

import java.util.List;

/**
 * LLM sohbet sağlayıcısı (Groq / OpenAI-uyumlu). Sistem promptu dahil mesaj listesini alır ve
 * gerekirse araçları (function-calling) çağırarak nihai asistan yanıt metnini döner.
 * Hata/timeout durumunda {@link AssistantUnavailableException} fırlatır.
 */
public interface AssistantChatPort {

    String complete(List<ChatMessage> messages, ToolContext ctx);

    /** Sağlayıcı erişilemez / yapılandırılmamış / kota dolu olduğunda fırlatılır. */
    class AssistantUnavailableException extends RuntimeException {
        public AssistantUnavailableException(String message) { super(message); }
        public AssistantUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
