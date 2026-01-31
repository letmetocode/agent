package com.getoffer.infrastructure.repository.planning;

import com.getoffer.domain.planning.model.entity.SopTemplateEntity;
import com.getoffer.domain.planning.adapter.repository.ISopTemplateRepository;
import com.getoffer.infrastructure.dao.SopTemplateDao;
import com.getoffer.infrastructure.dao.po.SopTemplatePO;
import com.getoffer.types.enums.SopStructureEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SOP 模板仓储实现
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Slf4j
@Repository
public class SopTemplateRepositoryImpl implements ISopTemplateRepository {

    private final SopTemplateDao sopTemplateDao;

    public SopTemplateRepositoryImpl(SopTemplateDao sopTemplateDao) {
        this.sopTemplateDao = sopTemplateDao;
    }

    @Override
    public SopTemplateEntity save(SopTemplateEntity entity) {
        entity.validate();
        SopTemplatePO po = toPO(entity);
        sopTemplateDao.insert(po);
        return toEntity(po);
    }

    @Override
    public SopTemplateEntity update(SopTemplateEntity entity) {
        entity.validate();
        SopTemplatePO po = toPO(entity);
        sopTemplateDao.update(po);
        return toEntity(po);
    }

    @Override
    public boolean deleteById(Long id) {
        return sopTemplateDao.deleteById(id) > 0;
    }

    @Override
    public SopTemplateEntity findById(Long id) {
        SopTemplatePO po = sopTemplateDao.selectById(id);
        return po != null ? toEntity(po) : null;
    }

    @Override
    public SopTemplateEntity findByCategoryAndNameAndVersion(String category, String name, Integer version) {
        SopTemplatePO po = sopTemplateDao.selectByCategoryAndNameAndVersion(category, name, version);
        return po != null ? toEntity(po) : null;
    }

    @Override
    public List<SopTemplateEntity> findByCategory(String category) {
        return sopTemplateDao.selectByCategory(category).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<SopTemplateEntity> findByCategoryAndName(String category, String name) {
        return sopTemplateDao.selectByCategoryAndName(category, name).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<SopTemplateEntity> findAll() {
        return sopTemplateDao.selectAll().stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<SopTemplateEntity> findByActive(Boolean isActive) {
        return sopTemplateDao.selectByActive(isActive).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<SopTemplateEntity> findByStructureType(String structureType) {
        return sopTemplateDao.selectByStructureType(structureType).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<SopTemplateEntity> searchByTriggerDescription(String keyword) {
        return sopTemplateDao.searchByTriggerDescription(keyword).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public SopTemplateEntity findLatestVersion(String category, String name) {
        List<SopTemplateEntity> templates = findByCategoryAndName(category, name);
        return templates.stream()
                .max((t1, t2) -> t1.getVersion().compareTo(t2.getVersion()))
                .orElse(null);
    }

    /**
     * PO 转换为 Entity
     */
    private SopTemplateEntity toEntity(SopTemplatePO po) {
        if (po == null) {
            return null;
        }
        SopTemplateEntity entity = new SopTemplateEntity();
        entity.setId(po.getId());
        entity.setCategory(po.getCategory());
        entity.setName(po.getName());
        entity.setVersion(po.getVersion());
        entity.setTriggerDescription(po.getTriggerDescription());
        entity.setStructureType(po.getStructureType());

        // JSONB 字段转换
        if (po.getGraphDefinition() != null) {
            entity.setGraphDefinition(com.alibaba.fastjson2.JSON.parseObject(po.getGraphDefinition()));
        }
        if (po.getInputSchema() != null) {
            entity.setInputSchema(com.alibaba.fastjson2.JSON.parseObject(po.getInputSchema()));
        }
        if (po.getDefaultConfig() != null) {
            entity.setDefaultConfig(com.alibaba.fastjson2.JSON.parseObject(po.getDefaultConfig()));
        }

        entity.setIsActive(po.getIsActive());
        entity.setCreatedBy(po.getCreatedBy());
        entity.setCreatedAt(po.getCreatedAt());
        entity.setUpdatedAt(po.getUpdatedAt());
        return entity;
    }

    /**
     * Entity 转换为 PO
     */
    private SopTemplatePO toPO(SopTemplateEntity entity) {
        if (entity == null) {
            return null;
        }
        SopTemplatePO po = SopTemplatePO.builder()
                .id(entity.getId())
                .category(entity.getCategory())
                .name(entity.getName())
                .version(entity.getVersion())
                .triggerDescription(entity.getTriggerDescription())
                .structureType(entity.getStructureType())
                .isActive(entity.getIsActive())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();

        // Map 转换为 JSON 字符串
        if (entity.getGraphDefinition() != null) {
            po.setGraphDefinition(com.alibaba.fastjson2.JSON.toJSONString(entity.getGraphDefinition()));
        }
        if (entity.getInputSchema() != null) {
            po.setInputSchema(com.alibaba.fastjson2.JSON.toJSONString(entity.getInputSchema()));
        }
        if (entity.getDefaultConfig() != null) {
            po.setDefaultConfig(com.alibaba.fastjson2.JSON.toJSONString(entity.getDefaultConfig()));
        }

        return po;
    }
}
