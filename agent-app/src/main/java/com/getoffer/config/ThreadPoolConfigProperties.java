package com.getoffer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 线程池配置属性类。
 * <p>
 * 从配置文件中读取线程池相关配置，配置前缀为 thread.pool.executor.config。
 * 支持的配置项包括核心线程数、最大线程数、存活时间、队列大小和拒绝策略。
 * </p>
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Data
@ConfigurationProperties(prefix = "thread.pool.executor.config", ignoreInvalidFields = true)
public class ThreadPoolConfigProperties {

    /** 核心线程数，默认20 */
    private Integer corePoolSize = 20;

    /** 最大线程数，默认200 */
    private Integer maxPoolSize = 200;

    /** 空闲线程最大存活时间（秒），默认10L */
    private Long keepAliveTime = 10L;

    /** 阻塞队列最大容量，默认5000 */
    private Integer blockQueueSize = 5000;

    /**
     * 拒绝策略，默认AbortPolicy。
     * <ul>
     *   <li>AbortPolicy：丢弃任务并抛出RejectedExecutionException异常</li>
     *   <li>DiscardPolicy：直接丢弃任务，不抛出异常</li>
     *   <li>DiscardOldestPolicy：将最早进入队列的任务删除，之后再尝试加入队列</li>
     *   <li>CallerRunsPolicy：如果任务添加线程池失败，主线程自己执行该任务</li>
     * </ul>
     */
    private String policy = "AbortPolicy";

}
