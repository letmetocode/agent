package com.getoffer.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

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
@RunWith(SpringRunner.class)
@SpringBootTest
public class ApiTest {

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
