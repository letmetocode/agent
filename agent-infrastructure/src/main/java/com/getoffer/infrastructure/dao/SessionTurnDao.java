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

    SessionTurnPO selectLatestBySessionIdAndClientMessageId(@Param("sessionId") Long sessionId,
                                                            @Param("clientMessageId") String clientMessageId);

    List<SessionTurnPO> selectBySessionId(@Param("sessionId") Long sessionId);

    List<SessionTurnPO> selectBySessionIdWithCursor(@Param("sessionId") Long sessionId,
                                                    @Param("cursor") Long cursor,
                                                    @Param("limit") Integer limit,
                                                    @Param("ascending") boolean ascending);

    SessionTurnPO selectLatestBySessionIdAndStatus(@Param("sessionId") Long sessionId,
                                                   @Param("status") TurnStatusEnum status);

    List<SessionTurnPO> selectPlanningTurnsOlderThan(@Param("cutoff") LocalDateTime cutoff,
                                                     @Param("limit") Integer limit);
}
