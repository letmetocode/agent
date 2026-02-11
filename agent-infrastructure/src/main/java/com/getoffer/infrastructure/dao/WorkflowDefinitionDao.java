package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.WorkflowDefinitionPO;
import com.getoffer.types.enums.WorkflowDefinitionStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Workflow Definition DAO。
 */
@Mapper
public interface WorkflowDefinitionDao {

    int insert(WorkflowDefinitionPO po);

    int update(WorkflowDefinitionPO po);

    WorkflowDefinitionPO selectById(@Param("id") Long id);

    List<WorkflowDefinitionPO> selectAll();

    List<WorkflowDefinitionPO> selectByStatus(@Param("status") WorkflowDefinitionStatusEnum status);

    WorkflowDefinitionPO selectLatestByTenantAndDefinitionKey(@Param("tenantId") String tenantId,
                                                              @Param("definitionKey") String definitionKey);
}

