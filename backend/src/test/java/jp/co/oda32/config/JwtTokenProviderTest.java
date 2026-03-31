package jp.co.oda32.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "test-secret-key-odamitsu-data-hub-must-be-at-least-256-bits-long-for-hmac",
                3600000
        );
    }

    @Test
    void generateAndValidateToken() {
        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        String token = jwtTokenProvider.generateToken(auth);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("testuser", jwtTokenProvider.getUsernameFromToken(token));
    }

    @Test
    void generateTokenFromUsername() {
        String token = jwtTokenProvider.generateToken("admin");

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("admin", jwtTokenProvider.getUsernameFromToken(token));
    }

    @Test
    void invalidTokenReturnsFalse() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.here"));
        assertFalse(jwtTokenProvider.validateToken(""));
        assertFalse(jwtTokenProvider.validateToken(null));
    }

    @Test
    void expiredTokenReturnsFalse() {
        JwtTokenProvider shortLived = new JwtTokenProvider(
                "test-secret-key-odamitsu-data-hub-must-be-at-least-256-bits-long-for-hmac",
                -1000 // already expired
        );
        String token = shortLived.generateToken("user");
        assertFalse(jwtTokenProvider.validateToken(token));
    }
}
