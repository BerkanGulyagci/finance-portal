package com.finance.portal.common.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    // Env yoksa local default'lar (local dev/docker bozulmaz). Cloud'da CORS_ALLOWED_ORIGINS
    // ile gerçek frontend origin'i (örn https://...) virgülle eklenir.
    // allowCredentials=true olduğu için wildcard (*) KULLANILAMAZ → açık liste şart.
    private static final String DEFAULT_ORIGINS =
            "http://localhost:5173,http://localhost:80,http://localhost";

    // KÖK SEBEP (cloud prod 403 "Invalid CORS request"): origin listesi @Value ile
    // tek bir köprü-property'den (cors.allowed-origins) okunuyordu. @Value RELAXED BINDING
    // YAPMAZ → CORS_ALLOWED_ORIGINS env'ini ancak application.yml'deki köprü satırı varsa görür.
    // Köprü satırını içermeyen ESKİ bir image deploy edildiğinde (ya da köprü silinince) @Value
    // sessizce :default'a (localhost) düşer ve gerçek cloud origin'i (https://...nip.io) reddedilir
    // — preflight 403 döner. Bu yüzden artık Environment'tan HEM köprü-property'yi HEM de env
    // değişkenini DOĞRUDAN okuyoruz: image stale olsa, köprü silinse bile env yine devreye girer.
    private final Environment environment;

    public CorsConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsFilter corsFilter() {
        List<String> allowedOrigins = resolveAllowedOrigins();
        log.info("CORS allowed origins resolved: {}", allowedOrigins);

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    /**
     * Önce köprü-property (cors.allowed-origins — application.yml), o yoksa env değişkeni
     * (CORS_ALLOWED_ORIGINS) DOĞRUDAN, ikisi de yoksa local default'lar. Böylece köprü-property'yi
     * içermeyen stale bir image'da bile cloud origin'i çalışır (dev hâlâ default'ları kullanır).
     */
    private List<String> resolveAllowedOrigins() {
        String raw = environment.getProperty("cors.allowed-origins");
        if (raw == null || raw.isBlank()) {
            // Köprü-property yoksa env'i doğrudan dene (@Value relaxed-binding yapmadığı için
            // application.yml köprüsü olmadan @Value bunu göremezdi — kök sebep buydu).
            raw = environment.getProperty("CORS_ALLOWED_ORIGINS");
        }
        if (raw == null || raw.isBlank()) {
            raw = DEFAULT_ORIGINS;
        }
        // Virgülle ayır + her parçayı trim et (olası boşluk/yeni-satır kenar durumlarına dayanıklı).
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
