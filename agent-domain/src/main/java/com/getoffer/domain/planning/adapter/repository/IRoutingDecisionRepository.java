package com.getoffer.domain.planning.adapter.repository;

import com.getoffer.domain.planning.model.entity.RoutingDecisionEntity;

/**
 * 路由决策仓储接口。
 */
public interface IRoutingDecisionRepository {

    RoutingDecisionEntity save(RoutingDecisionEntity entity);

    RoutingDecisionEntity findById(Long id);
}

