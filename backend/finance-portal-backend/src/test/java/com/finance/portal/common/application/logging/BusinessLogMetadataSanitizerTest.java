package com.finance.portal.common.application.logging;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessLogMetadataSanitizerTest {

    @Test
    void sanitize_returnsNullForNullOrEmpty() {
        assertNull(BusinessLogMetadataSanitizer.sanitize(null));
        assertNull(BusinessLogMetadataSanitizer.sanitize(Map.of()));
    }

    @Test
    void sanitize_removesAllListedSensitiveKeys() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("username", "alice");
        metadata.put("password", "p");
        metadata.put("currentPassword", "p");
        metadata.put("newPassword", "p");
        metadata.put("confirmPassword", "p");
        metadata.put("userPassword", "p");
        metadata.put("token", "t");
        metadata.put("accessToken", "t");
        metadata.put("refreshToken", "t");
        metadata.put("otp", "123456");
        metadata.put("verificationCode", "999");
        metadata.put("verificationToken", "vt");
        metadata.put("jwt", "eyJ...");
        metadata.put("ldapDn", "uid=alice,dc=example,dc=com");
        metadata.put("email", "alice@example.com");

        Map<String, Object> safe = BusinessLogMetadataSanitizer.sanitize(metadata);

        assertNotNull(safe);
        assertEquals("alice", safe.get("username"));
        assertEquals(1, safe.size());
    }

    @Test
    void sanitize_removesSensitiveKeys() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("username", "alice");
        metadata.put("password", "secret");
        metadata.put("currentPassword", "old");
        metadata.put("newPassword", "new");
        metadata.put("token", "jwt-token");
        metadata.put("email", "alice@example.com");
        metadata.put("emailDomain", "example.com");

        Map<String, Object> safe = BusinessLogMetadataSanitizer.sanitize(metadata);

        assertNotNull(safe);
        assertEquals("alice", safe.get("username"));
        assertEquals("example.com", safe.get("emailDomain"));
        assertFalse(safe.containsKey("password"));
        assertFalse(safe.containsKey("currentPassword"));
        assertFalse(safe.containsKey("newPassword"));
        assertFalse(safe.containsKey("token"));
        assertFalse(safe.containsKey("email"));
    }

    @Test
    void sanitize_removesValuesThatLookLikePlaintextEmail() {
        Map<String, Object> metadata = Map.of("note", "contact alice@example.com");
        assertNull(BusinessLogMetadataSanitizer.sanitize(metadata));
    }

    @Test
    void extractEmailDomain_returnsDomainOnly() {
        assertEquals("example.com", BusinessLogMetadataSanitizer.extractEmailDomain("User@Example.COM"));
        assertNull(BusinessLogMetadataSanitizer.extractEmailDomain("not-an-email"));
    }

    @Test
    void hashEmail_isDeterministicAndShort() {
        String hash1 = BusinessLogMetadataSanitizer.hashEmail("alice@example.com");
        String hash2 = BusinessLogMetadataSanitizer.hashEmail("alice@example.com");
        assertNotNull(hash1);
        assertEquals(16, hash1.length());
        assertEquals(hash1, hash2);
        assertTrue(hash2.matches("[0-9a-f]+"));
    }
}
