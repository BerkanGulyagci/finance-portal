package com.finance.portal.auth.infrastructure.config;

import com.finance.portal.auth.infrastructure.keycloak.KeycloakPortalProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KeycloakPortalProperties.class)
public class AuthKeycloakConfig {
}
