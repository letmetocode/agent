package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.TaskShareLinkPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务分享链接 DAO。
 */
@Mapper
public interface TaskShareLinkDao {

    int insert(TaskShareLinkPO po);

    TaskShareLinkPO selectById(@Param("id") Long id);

    TaskShareLinkPO selectByTaskIdAndShareCode(@Param("taskId") Long taskId,
                                               @Param("shareCode") String shareCode);

    List<TaskShareLinkPO> selectByTaskId(@Param("taskId") Long taskId);

    int revokeById(@Param("taskId") Long taskId,
                   @Param("id") Long id,
                   @Param("revokedReason") String revokedReason);

    int revokeAllByTaskId(@Param("taskId") Long taskId,
                          @Param("revokedReason") String revokedReason);
}
