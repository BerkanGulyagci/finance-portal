package com.finance.portal;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("@SpringBootTest boots the full context which requires a running Postgres. " +
        "Will be re-enabled in Phase 2 once Testcontainers provisions a real Postgres for CI.")
class ApplicationTests {

    @Test
    void contextLoads() {
    }

}
