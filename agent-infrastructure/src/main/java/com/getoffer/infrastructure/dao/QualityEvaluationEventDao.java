package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.QualityEvaluationEventPO;
import com.getoffer.infrastructure.dao.po.QualityExperimentSummaryPO;
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

    List<QualityEvaluationEventPO> selectByFiltersPaged(@Param("planId") Long planId,
                                                        @Param("taskId") Long taskId,
                                                        @Param("experimentKey") String experimentKey,
                                                        @Param("experimentVariant") String experimentVariant,
                                                        @Param("evaluatorType") String evaluatorType,
                                                        @Param("pass") Boolean pass,
                                                        @Param("keyword") String keyword,
                                                        @Param("offset") Integer offset,
                                                        @Param("limit") Integer limit);

    Long countByFilters(@Param("planId") Long planId,
                        @Param("taskId") Long taskId,
                        @Param("experimentKey") String experimentKey,
                        @Param("experimentVariant") String experimentVariant,
                        @Param("evaluatorType") String evaluatorType,
                        @Param("pass") Boolean pass,
                        @Param("keyword") String keyword);

    List<QualityExperimentSummaryPO> selectExperimentSummary(@Param("planId") Long planId,
                                                             @Param("experimentKey") String experimentKey,
                                                             @Param("evaluatorType") String evaluatorType,
                                                             @Param("limit") Integer limit);
}
