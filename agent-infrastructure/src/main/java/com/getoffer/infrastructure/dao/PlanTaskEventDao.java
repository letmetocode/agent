package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.PlanTaskEventPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Plan/Task 事件 DAO。
 */
@Mapper
public interface PlanTaskEventDao {

    int insert(PlanTaskEventPO po);

    List<PlanTaskEventPO> selectByPlanIdAfterEventId(@Param("planId") Long planId,
                                                     @Param("afterEventId") Long afterEventId,
                                                     @Param("limit") Integer limit);

    List<PlanTaskEventPO> selectLogsPaged(@Param("planIds") List<Long> planIds,
                                          @Param("taskId") Long taskId,
                                          @Param("level") String level,
                                          @Param("traceId") String traceId,
                                          @Param("keyword") String keyword,
                                          @Param("offset") Integer offset,
                                          @Param("limit") Integer limit);

    Long countLogs(@Param("planIds") List<Long> planIds,
                   @Param("taskId") Long taskId,
                   @Param("level") String level,
                   @Param("traceId") String traceId,
                   @Param("keyword") String keyword);
}
