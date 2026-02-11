package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.SessionTurnPO;
import com.getoffer.types.enums.TurnStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话回合 DAO。
 */
@Mapper
public interface SessionTurnDao {

    int insert(SessionTurnPO po);

    int update(SessionTurnPO po);

    SessionTurnPO selectById(@Param("id") Long id);

    SessionTurnPO selectByPlanId(@Param("planId") Long planId);

    List<SessionTurnPO> selectBySessionId(@Param("sessionId") Long sessionId);

    SessionTurnPO selectLatestBySessionIdAndStatus(@Param("sessionId") Long sessionId,
                                                   @Param("status") TurnStatusEnum status);
}
