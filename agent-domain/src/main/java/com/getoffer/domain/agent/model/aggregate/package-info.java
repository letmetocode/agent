/**
 * 聚合根层
 * <p>
 * 包含 Agent 系统的聚合根。
 * 聚合根是一组相关实体的根节点，负责维护聚合内部的一致性边界。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>聚合根通过唯一标识引用其他聚合根</li>
 *   <li>聚合内部的对象通过引用而非 ID 关联</li>
 *   <li>聚合根负责业务规则的执行和不变性维护</li>
 * </ul>
 *
 * @author getoffer
 * @since 2025-01-29
 */
package com.getoffer.domain.agent.model.aggregate;
