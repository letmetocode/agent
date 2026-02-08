package com.getoffer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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
    @Bean(name = "commonThreadPoolExecutor")
    @ConditionalOnMissingBean(name = "commonThreadPoolExecutor")
    public ThreadPoolExecutor threadPoolExecutor(ThreadPoolConfigProperties properties) {
        return new ThreadPoolExecutor(
                Math.max(properties.getCorePoolSize(), 1),
                Math.max(properties.getMaxPoolSize(), Math.max(properties.getCorePoolSize(), 1)),
                Math.max(properties.getKeepAliveTime(), 0L),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(Math.max(properties.getBlockQueueSize(), 1)),
                Executors.defaultThreadFactory(),
                buildRejectedExecutionHandler(properties.getPolicy()));
    }

    /**
     * Task 执行专用线程池：
     * 1) 与调度线程解耦，避免 @Scheduled 线程被 LLM 长调用阻塞；
     * 2) 默认 queue-capacity=0，确保 claim 后任务能尽快开始执行，降低 lease 空转风险。
     */
    @Bean(name = "taskExecutionWorker")
    @ConditionalOnMissingBean(name = "taskExecutionWorker")
    public ThreadPoolExecutor taskExecutionWorker(
            @Value("${executor.worker.core-size:8}") int coreSize,
            @Value("${executor.worker.max-size:8}") int maxSize,
            @Value("${executor.worker.keep-alive-seconds:60}") long keepAliveSeconds,
            @Value("${executor.worker.queue-capacity:0}") int queueCapacity,
            @Value("${executor.worker.rejection-policy:AbortPolicy}") String rejectionPolicy,
            @Value("${executor.worker.thread-name-prefix:task-exec-worker-}") String threadNamePrefix) {
        int normalizedCoreSize = Math.max(coreSize, 1);
        int normalizedMaxSize = Math.max(maxSize, normalizedCoreSize);
        long normalizedKeepAliveSeconds = Math.max(keepAliveSeconds, 0L);
        int normalizedQueueCapacity = Math.max(queueCapacity, 0);
        BlockingQueue<Runnable> queue = normalizedQueueCapacity == 0
                ? new SynchronousQueue<>()
                : new LinkedBlockingQueue<>(normalizedQueueCapacity);
        AtomicInteger threadIndex = new AtomicInteger(0);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(threadNamePrefix + threadIndex.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                normalizedCoreSize,
                normalizedMaxSize,
                normalizedKeepAliveSeconds,
                TimeUnit.SECONDS,
                queue,
                threadFactory,
                buildRejectedExecutionHandler(rejectionPolicy));
        executor.allowCoreThreadTimeOut(false);
        return executor;
    }

    private RejectedExecutionHandler buildRejectedExecutionHandler(String policy) {
        if ("DiscardPolicy".equals(policy)) {
            return new ThreadPoolExecutor.DiscardPolicy();
        }
        if ("DiscardOldestPolicy".equals(policy)) {
            return new ThreadPoolExecutor.DiscardOldestPolicy();
        }
        if ("CallerRunsPolicy".equals(policy)) {
            return new ThreadPoolExecutor.CallerRunsPolicy();
        }
        if ("AbortPolicy".equals(policy)) {
            return new ThreadPoolExecutor.AbortPolicy();
        }
        log.warn("Unknown rejection policy '{}', fallback to AbortPolicy", policy);
        return new ThreadPoolExecutor.AbortPolicy();
    }

}
