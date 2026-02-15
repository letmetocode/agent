package com.getoffer.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;

/**
 * API 测试类。
 * <p>
 * 提供基础API测试功能，用于验证应用程序启动和基本功能。
 * </p>
 *
 * @author getoffer
 * @since 2025-01-29
 */
@Slf4j
@SpringBootTest(classes = ApiTest.TestApplication.class)
public class ApiTest {

    @SpringBootApplication(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            MybatisAutoConfiguration.class
    })
    static class TestApplication {
    }

    /**
     * 基础测试方法。
     * <p>
     * 用于验证Spring上下文加载和日志输出功能。
     * </p>
     */
    @Test
    public void test() {
        log.info("测试完成");
    }

}
