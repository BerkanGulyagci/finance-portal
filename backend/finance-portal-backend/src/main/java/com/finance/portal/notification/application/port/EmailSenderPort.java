package com.finance.portal.notification.application.port;

/**
 * Basit e-posta gönderim portu. Implementasyonu JavaMailSender (SMTP/Gmail) kullanır.
 * Gönderim başarısız olursa {@link EmailSendException} fırlatır.
 */
public interface EmailSenderPort {

    void send(String to, String subject, String htmlBody);

    /** E-posta gönderiminde oluşan hatayı sarmalar. */
    class EmailSendException extends RuntimeException {
        public EmailSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
