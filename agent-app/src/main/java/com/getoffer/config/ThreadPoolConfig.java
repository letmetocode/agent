package com.getoffer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;

/**
 * 线程池配置类。
 * <p>
 * 配置异步执行所需的线程池，支持自定义线程池参数：
 * <ul>
 *   <li>核心线程数：默认20</li>
 *   <li>最大线程数：默认200</li>
 *   <li>队列大小：默认5000</li>
 *   <li>拒绝策略：支持AbortPolicy、DiscardPolicy、DiscardOldestPolicy、CallerRunsPolicy</li>
 * </ul>
 * </p>
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Slf4j
@EnableAsync
@Configuration
@EnableConfigurationProperties(ThreadPoolConfigProperties.class)
public class ThreadPoolConfig {

    /**
     * 创建线程池执行器Bean。
     * <p>
     * 根据配置文件中的参数创建ThreadPoolExecutor，支持四种拒绝策略：
     * <ul>
     *   <li>AbortPolicy：丢弃任务并抛出异常（默认）</li>
     *   <li>DiscardPolicy：直接丢弃任务，不抛出异常</li>
     *   <li>DiscardOldestPolicy：丢弃队列中最老的任务，然后重试</li>
     *   <li>CallerRunsPolicy：由调用线程执行该任务</li>
     * </ul>
     * </p>
     *
     * @param properties 线程池配置属性
     * @return 线程池执行器实例
     */
    @Bean
    @ConditionalOnMissingBean(ThreadPoolExecutor.class)
    public ThreadPoolExecutor threadPoolExecutor(ThreadPoolConfigProperties properties) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        // 实例化策略
        RejectedExecutionHandler handler;
        switch (properties.getPolicy()){
            case "AbortPolicy":
                handler = new ThreadPoolExecutor.AbortPolicy();
                break;
            case "DiscardPolicy":
                handler = new ThreadPoolExecutor.DiscardPolicy();
                break;
            case "DiscardOldestPolicy":
                handler = new ThreadPoolExecutor.DiscardOldestPolicy();
                break;
            case "CallerRunsPolicy":
                handler = new ThreadPoolExecutor.CallerRunsPolicy();
                break;
            default:
                handler = new ThreadPoolExecutor.AbortPolicy();
                break;
        }
        // 创建线程池
        return new ThreadPoolExecutor(properties.getCorePoolSize(),
                properties.getMaxPoolSize(),
                properties.getKeepAliveTime(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getBlockQueueSize()),
                Executors.defaultThreadFactory(),
                handler);
    }

}
