package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.QualityEvaluationEventPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 质量评估事件 DAO。
 */
@Mapper
public interface QualityEvaluationEventDao {

    int insert(QualityEvaluationEventPO po);

    List<QualityEvaluationEventPO> selectByPlanId(@Param("planId") Long planId,
                                                  @Param("limit") Integer limit);
}
