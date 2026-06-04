package com.finance.portal.common.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Global OpenAPI 3 tanımı. springdoc bu annotation'ları okuyup
 * /v3/api-docs (JSON spec) ve /swagger-ui.html (interaktif UI) üretir.
 *
 * <p>Endpoint'ler controller'lardan otomatik taranır; burada sadece
 * genel API bilgisi ve JWT (Keycloak Bearer token) güvenlik şeması tanımlıdır.
 * "bearerAuth" şeması Swagger UI'daki "Authorize" butonunu aktive eder:
 * korumalı endpoint'leri test ederken token girilebilir.</p>
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Finance Portal API",
                version = "v1",
                description = """
                        Portiva finans portföy uygulamasının REST API'si.
                        Piyasa verileri, portföy yönetimi, alarmlar, haberler,
                        bülten ve AI asistan uç noktalarını içerir.
                        Tüm uç noktalar /api/v1/ altında sürümlenmiştir.""",
                contact = @Contact(name = "Finance Portal", email = "bgulyaci@gmail.com"),
                license = @License(name = "Proprietary")
        ),
        servers = {
                @Server(url = "/", description = "Geçerli ortam")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        description = "Keycloak'tan alınan JWT access token. 'Bearer ' önekiyle gönderilir.",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
