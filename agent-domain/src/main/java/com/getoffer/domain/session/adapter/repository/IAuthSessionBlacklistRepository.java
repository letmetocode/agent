package com.getoffer.domain.session.adapter.repository;

import com.getoffer.domain.session.model.entity.AuthSessionBlacklistEntity;

import java.time.LocalDateTime;

/**
 * JWT 登录态吊销黑名单仓储。
 */
public interface IAuthSessionBlacklistRepository {

    void save(AuthSessionBlacklistEntity entity);

    boolean existsActiveByJti(String jti, LocalDateTime now);
}
