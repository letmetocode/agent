package com.getoffer.domain.planning.adapter.repository;

import com.getoffer.domain.planning.model.entity.SopTemplateEntity;

import java.util.List;

/**
 * SOP 模板仓储接口
 *
 * @author getoffer
 * @since 2025-01-29
 */
public interface ISopTemplateRepository {

    /**
     * 保存 SOP 模板
     */
    SopTemplateEntity save(SopTemplateEntity entity);

    /**
     * 更新 SOP 模板
     */
    SopTemplateEntity update(SopTemplateEntity entity);

    /**
     * 根据 ID 删除
     */
    boolean deleteById(Long id);

    /**
     * 根据 ID 查询
     */
    SopTemplateEntity findById(Long id);

    /**
     * 根据分类、名称和版本查询
     */
    SopTemplateEntity findByCategoryAndNameAndVersion(String category, String name, Integer version);

    /**
     * 根据分类查询
     */
    List<SopTemplateEntity> findByCategory(String category);

    /**
     * 根据分类和名称查询所有版本
     */
    List<SopTemplateEntity> findByCategoryAndName(String category, String name);

    /**
     * 查询所有模板
     */
    List<SopTemplateEntity> findAll();

    /**
     * 根据激活状态查询
     */
    List<SopTemplateEntity> findByActive(Boolean isActive);

    /**
     * 根据结构类型查询
     */
    List<SopTemplateEntity> findByStructureType(String structureType);

    /**
     * 全文搜索触发描述
     */
    List<SopTemplateEntity> searchByTriggerDescription(String keyword);

    /**
     * 获取最新版本的模板
     */
    SopTemplateEntity findLatestVersion(String category, String name);
}
