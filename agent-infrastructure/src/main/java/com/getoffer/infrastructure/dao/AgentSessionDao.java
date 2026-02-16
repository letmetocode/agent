package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.AgentSessionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户会话 DAO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Mapper
public interface AgentSessionDao {

    /**
     * 插入会话
     */
    int insert(AgentSessionPO po);

    /**
     * 根据 ID 更新
     */
    int update(AgentSessionPO po);

    /**
     * 根据 ID 删除
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据 ID 查询
     */
    AgentSessionPO selectById(@Param("id") Long id);

    /**
     * 根据用户 ID 查询
     */
    List<AgentSessionPO> selectByUserId(@Param("userId") String userId);

    /**
     * 根据用户 ID 查询活跃会话
     */
    List<AgentSessionPO> selectActiveByUserId(@Param("userId") String userId);

    /**
     * 查询所有会话
     */
    List<AgentSessionPO> selectAll();

    /**
     * 统计会话总数。
     */
    Long countAll();

    /**
     * 按激活状态统计会话数量。
     */
    Long countByActive(@Param("isActive") Boolean isActive);

    /**
     * 根据激活状态查询
     */
    List<AgentSessionPO> selectByActive(@Param("isActive") Boolean isActive);

    /**
     * 按用户维度统计会话数量（支持激活态与关键字过滤）。
     */
    Long countByUserIdAndFilters(@Param("userId") String userId,
                                 @Param("activeOnly") Boolean activeOnly,
                                 @Param("keyword") String keyword);

    /**
     * 按用户维度分页查询会话（支持激活态与关键字过滤）。
     */
    List<AgentSessionPO> selectByUserIdAndFiltersPaged(@Param("userId") String userId,
                                                       @Param("activeOnly") Boolean activeOnly,
                                                       @Param("keyword") String keyword,
                                                       @Param("offset") Integer offset,
                                                       @Param("limit") Integer limit);

    /**
     * 关闭用户的所有活跃会话
     */
    int closeActiveSessionsByUserId(@Param("userId") String userId);
}
