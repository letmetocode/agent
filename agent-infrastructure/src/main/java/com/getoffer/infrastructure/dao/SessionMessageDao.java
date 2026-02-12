package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.SessionMessagePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话消息 DAO。
 */
@Mapper
public interface SessionMessageDao {

    int insert(SessionMessagePO po);

    SessionMessagePO selectFinalAssistantByTurnId(@Param("turnId") Long turnId);

    SessionMessagePO selectLatestAssistantByTurnId(@Param("turnId") Long turnId);

    SessionMessagePO selectById(@Param("id") Long id);

    List<SessionMessagePO> selectByTurnId(@Param("turnId") Long turnId);

    List<SessionMessagePO> selectBySessionId(@Param("sessionId") Long sessionId);
}
