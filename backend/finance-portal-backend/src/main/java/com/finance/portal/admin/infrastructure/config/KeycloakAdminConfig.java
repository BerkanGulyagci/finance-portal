package com.finance.portal.admin.infrastructure.config;

import com.finance.portal.admin.infrastructure.keycloak.KeycloakAdminProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KeycloakAdminProperties.class)
public class KeycloakAdminConfig {
}
