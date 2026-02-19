package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.api.dto.AuthLoginRequestDTO;
import com.getoffer.api.dto.AuthLoginResponseDTO;
import com.getoffer.domain.session.adapter.repository.IAuthSessionBlacklistRepository;
import com.getoffer.domain.session.model.entity.AuthSessionBlacklistEntity;
import com.getoffer.trigger.application.command.AuthSessionCommandService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthSessionCommandServiceTest {

    @Test
    public void shouldRevokeTokenAfterLogout() {
        InMemoryBlacklistRepository blacklistRepository = new InMemoryBlacklistRepository();
        AuthSessionCommandService service = new AuthSessionCommandService(
                "admin",
                "admin123",
                "Operator",
                24,
                60,
                "agent-app",
                "unit-test-secret",
                new ObjectMapper(),
                blacklistRepository
        );

        AuthLoginRequestDTO request = new AuthLoginRequestDTO();
        request.setUsername("admin");
        request.setPassword("admin123");
        AuthLoginResponseDTO login = service.login(request);

        Assertions.assertNotNull(login.getToken());
        Assertions.assertDoesNotThrow(() -> service.requireValidToken(login.getToken()));

        Assertions.assertTrue(service.logout("Bearer " + login.getToken()).getSuccess());

        IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.requireValidToken(login.getToken())
        );
        Assertions.assertTrue(ex.getMessage().contains("失效"));
    }

    @Test
    public void shouldRejectTamperedToken() {
        AuthSessionCommandService service = new AuthSessionCommandService(
                "admin",
                "admin123",
                "Operator",
                24,
                60,
                "agent-app",
                "unit-test-secret",
                new ObjectMapper(),
                new InMemoryBlacklistRepository()
        );
        AuthLoginRequestDTO request = new AuthLoginRequestDTO();
        request.setUsername("admin");
        request.setPassword("admin123");
        AuthLoginResponseDTO login = service.login(request);

        String tampered = login.getToken().substring(0, login.getToken().length() - 2) + "ab";
        Assertions.assertThrows(IllegalArgumentException.class, () -> service.requireValidToken(tampered));
    }

    private static class InMemoryBlacklistRepository implements IAuthSessionBlacklistRepository {

        private final Map<String, LocalDateTime> revoked = new ConcurrentHashMap<>();

        @Override
        public void save(AuthSessionBlacklistEntity entity) {
            if (entity == null || entity.getJti() == null) {
                return;
            }
            revoked.put(entity.getJti(), entity.getExpiredAt());
        }

        @Override
        public boolean existsActiveByJti(String jti, LocalDateTime now) {
            LocalDateTime expiredAt = revoked.get(jti);
            return expiredAt != null && expiredAt.isAfter(now == null ? LocalDateTime.now() : now);
        }
    }
}
