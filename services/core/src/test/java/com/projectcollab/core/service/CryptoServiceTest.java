package com.projectcollab.core.service;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class CryptoServiceTest {
    @Test
    void encryptsWithRandomNonceAndDecrypts() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        CryptoService crypto = new CryptoService(key);
        String first = crypto.encrypt("sensitive-token");
        String second = crypto.encrypt("sensitive-token");
        assertThat(first).isNotEqualTo(second);
        assertThat(crypto.decrypt(first)).isEqualTo("sensitive-token");
        assertThat(first).doesNotContain("sensitive-token");
    }

    @Test
    void rejectsWrongKeyLength() {
        String key = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new CryptoService(key)).isInstanceOf(IllegalStateException.class);
    }
}

