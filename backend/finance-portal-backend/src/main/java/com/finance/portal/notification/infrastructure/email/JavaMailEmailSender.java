package com.finance.portal.notification.infrastructure.email;

import com.finance.portal.common.application.logging.BusinessLogMetadataSanitizer;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.notification.application.port.EmailSenderPort;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SMTP/Gmail üzerinden HTML e-posta gönderir (spring-boot-starter-mail / JavaMailSender).
 * Kimlik bilgileri {@code spring.mail.*} (kök .env.local'den gelen SMTP_* env'leri) ile gelir.
 */
@Component
public class JavaMailEmailSender implements EmailSenderPort {

    private static final Logger log = LoggerFactory.getLogger(JavaMailEmailSender.class);

    private final JavaMailSender mailSender;
    private final CentralIntegrationLogService integrationLog;
    private final String from;
    private final String fromDisplayName;

    /** true → loglarda e-posta maskelenir (b***5@gmail.com). false → tam adres (yalnız güvenilir/iç ortamlar). */
    @Value("${app.logging.mask-email:true}")
    private boolean maskEmailInLogs;

    public JavaMailEmailSender(JavaMailSender mailSender,
                               CentralIntegrationLogService integrationLog,
                               @Value("${alarm.mail.from:}") String from,
                               @Value("${alarm.mail.from-display-name:FinansPortalı}") String fromDisplayName) {
        this.mailSender = mailSender;
        this.integrationLog = integrationLog;
        this.from = from;
        this.fromDisplayName = fromDisplayName;
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        if (to == null || to.isBlank()) {
            throw new EmailSendException("Recipient e-mail is blank", null);
        }
        long start = System.nanoTime();
        String masked = maskEmailInLogs ? BusinessLogMetadataSanitizer.maskEmail(to) : to;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            if (from != null && !from.isBlank()) {
                helper.setFrom(buildFrom());
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("E-mail sent to {}", masked);
            integrationLog.publish(IntegrationLogSupport.EVENT_EMAIL_SENT, "INFO",
                    "E-mail sent to " + masked, IntegrationLogSupport.PROVIDER_SMTP,
                    IntegrationLogSupport.OPERATION_EMAIL, "200", elapsedMs(start), false, false,
                    meta(to, subject, masked), JavaMailEmailSender.class.getName());
        } catch (Exception ex) {
            log.warn("Failed to send e-mail to {}: {}", masked, ex.getMessage());
            integrationLog.publish(IntegrationLogSupport.EVENT_EMAIL_FAILED, "ERROR",
                    "Failed to send e-mail to " + masked + ": " + ex.getMessage(), IntegrationLogSupport.PROVIDER_SMTP,
                    IntegrationLogSupport.OPERATION_EMAIL, null, elapsedMs(start), false, true,
                    meta(to, subject, masked), JavaMailEmailSender.class.getName());
            throw new EmailSendException("Failed to send e-mail to " + masked, ex);
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    // E-posta adresi maskeli + domain olarak loglanır (ham adres tutulmaz).
    private static Map<String, Object> meta(String to, String subject, String masked) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("to", masked);
        m.put("toDomain", BusinessLogMetadataSanitizer.extractEmailDomain(to));
        m.put("subject", subject);
        return m;
    }

    private InternetAddress buildFrom() throws UnsupportedEncodingException {
        return new InternetAddress(from, fromDisplayName, StandardCharsets.UTF_8.name());
    }
}
