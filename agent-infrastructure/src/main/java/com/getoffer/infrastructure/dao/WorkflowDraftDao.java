package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.WorkflowDraftPO;
import com.getoffer.types.enums.WorkflowDraftStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Workflow Draft DAO。
 */
@Mapper
public interface WorkflowDraftDao {

    int insert(WorkflowDraftPO po);

    int update(WorkflowDraftPO po);

    WorkflowDraftPO selectById(@Param("id") Long id);

    List<WorkflowDraftPO> selectAll();

    List<WorkflowDraftPO> selectByStatus(@Param("status") WorkflowDraftStatusEnum status);

    WorkflowDraftPO selectLatestByDedupHash(@Param("dedupHash") String dedupHash);
}

