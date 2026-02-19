package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.AuthSessionBlacklistPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * JWT 登录态黑名单 DAO。
 */
@Mapper
public interface AuthSessionBlacklistDao {

    int upsert(AuthSessionBlacklistPO po);

    int countActiveByJti(@Param("jti") String jti,
                         @Param("now") LocalDateTime now);
}
