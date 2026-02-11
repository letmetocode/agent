package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.RoutingDecisionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 路由决策 DAO。
 */
@Mapper
public interface RoutingDecisionDao {

    int insert(RoutingDecisionPO po);

    RoutingDecisionPO selectById(@Param("id") Long id);
}

