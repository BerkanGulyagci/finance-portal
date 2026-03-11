package com.finance.portal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTests {

    @Autowired
    MockMvc mockMvc;

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtWithRoles(String... roles) {
        Jwt jwt = new Jwt(
                "mock-token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "test-user",
                        "preferred_username", "test-user",
                        "realm_access", Map.of("roles", List.of(roles))
                )
        );

        return SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt);
    }

    @Test
    void marketEndpointsShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/market/stocks/THYAO.IS").accept(APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void newsEndpointsShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/news").accept(APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void kafkaTestShouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(post("/api/kafka/test").accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void kafkaTestShouldReturn200WithValidJwt() throws Exception {
        mockMvc.perform(
                        post("/api/kafka/test")
                                .with(jwtWithRoles("USER"))
                                .accept(APPLICATION_JSON)
                )
                .andExpect(status().isOk());
    }
}

