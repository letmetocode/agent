package com.getoffer.infrastructure.repository.session;

import com.getoffer.domain.session.adapter.repository.IAuthSessionBlacklistRepository;
import com.getoffer.domain.session.model.entity.AuthSessionBlacklistEntity;
import com.getoffer.infrastructure.dao.AuthSessionBlacklistDao;
import com.getoffer.infrastructure.dao.po.AuthSessionBlacklistPO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * JWT 登录态吊销黑名单仓储实现。
 */
@Repository
public class AuthSessionBlacklistRepositoryImpl implements IAuthSessionBlacklistRepository {

    private final AuthSessionBlacklistDao authSessionBlacklistDao;

    public AuthSessionBlacklistRepositoryImpl(AuthSessionBlacklistDao authSessionBlacklistDao) {
        this.authSessionBlacklistDao = authSessionBlacklistDao;
    }

    @Override
    public void save(AuthSessionBlacklistEntity entity) {
        if (entity == null) {
            return;
        }
        authSessionBlacklistDao.upsert(toPO(entity));
    }

    @Override
    public boolean existsActiveByJti(String jti, LocalDateTime now) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        LocalDateTime checkTime = now == null ? LocalDateTime.now() : now;
        return authSessionBlacklistDao.countActiveByJti(jti, checkTime) > 0;
    }

    private AuthSessionBlacklistPO toPO(AuthSessionBlacklistEntity entity) {
        return AuthSessionBlacklistPO.builder()
                .id(entity.getId())
                .jti(entity.getJti())
                .userId(entity.getUserId())
                .expiredAt(entity.getExpiredAt())
                .revokedAt(entity.getRevokedAt())
                .revokeReason(entity.getRevokeReason())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
