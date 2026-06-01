package com.finance.portal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring Boot context smoke test. A real PostgreSQL container is provisioned
 * by Testcontainers (no host Postgres needed); Spring's datasource is wired
 * to it via the shared {@link AbstractIntegrationTest} base, so Flyway
 * migrations can run.
 */
@SpringBootTest
class ApplicationIT extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
