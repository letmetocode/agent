package com.getoffer.infrastructure.dao;

import com.getoffer.infrastructure.dao.po.SopTemplatePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * SOP 模板 DAO
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Mapper
public interface SopTemplateDao {

    /**
     * 插入 SOP 模板
     */
    int insert(SopTemplatePO po);

    /**
     * 根据 ID 更新
     */
    int update(SopTemplatePO po);

    /**
     * 根据 ID 删除
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据 ID 查询
     */
    SopTemplatePO selectById(@Param("id") Long id);

    /**
     * 根据分类、名称和版本查询
     */
    SopTemplatePO selectByCategoryAndNameAndVersion(@Param("category") String category,
                                                     @Param("name") String name,
                                                     @Param("version") Integer version);

    /**
     * 根据分类查询
     */
    List<SopTemplatePO> selectByCategory(@Param("category") String category);

    /**
     * 根据分类和名称查询所有版本
     */
    List<SopTemplatePO> selectByCategoryAndName(@Param("category") String category,
                                                 @Param("name") String name);

    /**
     * 查询所有模板
     */
    List<SopTemplatePO> selectAll();

    /**
     * 根据激活状态查询
     */
    List<SopTemplatePO> selectByActive(@Param("isActive") Boolean isActive);

    /**
     * 根据结构类型查询
     */
    List<SopTemplatePO> selectByStructureType(@Param("structureType") String structureType);

    /**
     * 全文搜索触发描述
     */
    List<SopTemplatePO> searchByTriggerDescription(@Param("keyword") String keyword);
}
