package com.getoffer.domain.session.adapter.repository;

import com.getoffer.domain.session.model.entity.AgentSessionEntity;

import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户会话仓储接口
 *
 * @author getoffer
 * @since 2025-01-29
 */
public interface IAgentSessionRepository {

    /**
     * 保存会话
     */
    AgentSessionEntity save(AgentSessionEntity entity);

    /**
     * 更新会话
     */
    AgentSessionEntity update(AgentSessionEntity entity);

    /**
     * 根据 ID 删除
     */
    boolean deleteById(Long id);

    /**
     * 根据 ID 查询
     */
    AgentSessionEntity findById(Long id);

    /**
     * 根据用户 ID 查询
     */
    List<AgentSessionEntity> findByUserId(String userId);

    /**
     * 根据用户 ID 查询活跃会话
     */
    List<AgentSessionEntity> findActiveByUserId(String userId);

    /**
     * 查询所有会话
     */
    List<AgentSessionEntity> findAll();

    /**
     * 统计会话总数。
     */
    default long countAll() {
        List<AgentSessionEntity> sessions = findAll();
        return sessions == null ? 0L : sessions.size();
    }

    /**
     * 按激活状态统计会话数量。
     */
    default long countByActive(Boolean isActive) {
        if (isActive == null) {
            return 0L;
        }
        List<AgentSessionEntity> sessions = findByActive(isActive);
        return sessions == null ? 0L : sessions.size();
    }

    /**
     * 根据激活状态查询
     */
    List<AgentSessionEntity> findByActive(Boolean isActive);

    /**
     * 按用户维度统计会话数量（支持激活态和关键字过滤）。
     */
    default long countByUserIdAndFilters(String userId, Boolean activeOnly, String keyword) {
        return applySessionFilters(userId, activeOnly, keyword).size();
    }

    /**
     * 按用户维度分页查询会话（支持激活态和关键字过滤）。
     */
    default List<AgentSessionEntity> findByUserIdAndFiltersPaged(String userId,
                                                                 Boolean activeOnly,
                                                                 String keyword,
                                                                 int offset,
                                                                 int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<AgentSessionEntity> filtered = applySessionFilters(userId, activeOnly, keyword);
        int safeOffset = Math.max(0, offset);
        if (safeOffset >= filtered.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(safeOffset + limit, filtered.size());
        return filtered.subList(safeOffset, toIndex);
    }

    /**
     * 关闭用户的所有活跃会话
     */
    boolean closeActiveSessionsByUserId(String userId);

    private List<AgentSessionEntity> applySessionFilters(String userId, Boolean activeOnly, String keyword) {
        if (userId == null || userId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentSessionEntity> source = Boolean.TRUE.equals(activeOnly) ? findActiveByUserId(userId) : findByUserId(userId);
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return source.stream()
                .filter(Objects::nonNull)
                .filter(item -> normalizedKeyword.isEmpty() || containsKeyword(item, normalizedKeyword))
                .sorted(Comparator
                        .comparing(AgentSessionEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AgentSessionEntity::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private static boolean containsKeyword(AgentSessionEntity session, String keyword) {
        String haystack = String.format("%s %s %s %s",
                        session.getId() == null ? "" : session.getId(),
                        safeText(session.getTitle()),
                        safeText(session.getAgentKey()),
                        safeText(session.getScenario()))
                .toLowerCase(Locale.ROOT);
        return haystack.contains(keyword);
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }
}
