package com.finance.portal.assistant.application.tools;

/**
 * Araç çalıştırma bağlamı. Portföy/alarm/favori araçları userId ile auth kontrolü yapar;
 * userEmail alarm e-posta bildirimi için, userName kişiselleştirme (adıyla hitap) için.
 * Anonim kullanıcıda hepsi null.
 */
public record ToolContext(String userId, String userName, String userEmail) {

    public boolean isAuthenticated() {
        return userId != null && !userId.isBlank();
    }
}
