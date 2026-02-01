package com.getoffer;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Agent 框架应用启动类。
 * <p>
 * 这是整个应用程序的入口点，负责启动Spring Boot应用并初始化所有组件。
 * Application类位于顶层包路径，确保能够扫描到所有子模块中的组件。
 * </p>
 *
 * @author getoffer
 * @since 2025-01-29
 */
@SpringBootApplication
@Configurable
public class Application {

    /**
     * 应用程序主入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args){
        SpringApplication.run(Application.class);
    }

}
