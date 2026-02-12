package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.SessionTurnPO;
import com.getoffer.types.enums.TurnStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话回合 DAO。
 */
@Mapper
public interface SessionTurnDao {

    int insert(SessionTurnPO po);

    int update(SessionTurnPO po);

    int updateToTerminalIfNotTerminal(@Param("id") Long id,
                                      @Param("status") TurnStatusEnum status,
                                      @Param("assistantSummary") String assistantSummary,
                                      @Param("completedAt") LocalDateTime completedAt);

    int bindFinalResponseMessageIfAbsent(@Param("id") Long id,
                                         @Param("messageId") Long messageId);

    SessionTurnPO selectById(@Param("id") Long id);

    SessionTurnPO selectByPlanId(@Param("planId") Long planId);

    List<SessionTurnPO> selectBySessionId(@Param("sessionId") Long sessionId);

    SessionTurnPO selectLatestBySessionIdAndStatus(@Param("sessionId") Long sessionId,
                                                   @Param("status") TurnStatusEnum status);
}
