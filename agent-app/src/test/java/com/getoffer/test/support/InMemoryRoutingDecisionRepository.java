package com.getoffer.test.support;

import com.getoffer.domain.planning.adapter.repository.IRoutingDecisionRepository;
import com.getoffer.domain.planning.model.entity.RoutingDecisionEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内存路由决策仓储。
 */
public class InMemoryRoutingDecisionRepository implements IRoutingDecisionRepository {

    private final Map<Long, RoutingDecisionEntity> store = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public RoutingDecisionEntity save(RoutingDecisionEntity entity) {
        if (entity.getId() == null) {
            entity.setId(nextId++);
        }
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public RoutingDecisionEntity findById(Long id) {
        return store.get(id);
    }
}

