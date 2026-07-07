package com.berzantas.kanban.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PasswordConfigTest {

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void encodesToArgon2idAndVerifies() {
        String hash = passwordEncoder.encode("password123");
        assertThat(hash).startsWith("$argon2id$");
        assertThat(passwordEncoder.matches("password123", hash)).isTrue();
        assertThat(passwordEncoder.matches("wrong", hash)).isFalse();
    }
}
