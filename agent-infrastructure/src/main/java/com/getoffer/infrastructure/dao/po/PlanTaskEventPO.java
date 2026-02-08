package com.getoffer.infrastructure.dao.po;

import com.getoffer.types.enums.PlanTaskEventTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Plan/Task 事件 PO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanTaskEventPO {

    private Long id;
    private Long planId;
    private Long taskId;
    private PlanTaskEventTypeEnum eventType;
    private String eventData;
    private LocalDateTime createdAt;
}
