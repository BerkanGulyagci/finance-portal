package com.finance.portal.portfolio.infrastructure.viop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

import java.io.IOException;
import java.util.Properties;

/**
 * VİOP contract specs YAML'ını ayrı bir property kaynağı olarak yükler.
 * Spring Boot {@code application.yml}'yi otomatik okur ama ek bir {@code viop-contract-specs.yml}
 * için {@link PropertySource} kullanmamız gerekiyor. YAML formatı için custom factory.
 */
@Configuration
@EnableConfigurationProperties(ViopContractSpecProperties.class)
@PropertySource(value = "classpath:viop-contract-specs.yml", factory = ViopContractSpecConfig.YamlFactory.class)
public class ViopContractSpecConfig {

    public static class YamlFactory implements PropertySourceFactory {
        @Override
        public org.springframework.core.env.PropertySource<?> createPropertySource(String name,
                                                                                    EncodedResource resource) throws IOException {
            org.springframework.beans.factory.config.YamlPropertiesFactoryBean factory =
                    new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
            factory.setResources(resource.getResource());
            Properties props = factory.getObject();
            String sourceName = name != null ? name : resource.getResource().getFilename();
            return new org.springframework.core.env.PropertiesPropertySource(sourceName, props);
        }
    }
}
