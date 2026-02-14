package com.getoffer.test.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PostgresIntegrationTestSupport {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agent_it")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("sql/integration-schema.sql");

    static {
        bridgeDockerApiVersionFromEnv();
        ensurePostgresStarted();
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        ensurePostgresStarted();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.datasource.type", () -> "com.zaxxer.hikari.HikariDataSource");
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        registry.add("event.publisher.instance-id", () -> "it-instance");
        registry.add("event.notify.channel", () -> "plan_task_events_channel");
    }

    private static synchronized void ensurePostgresStarted() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
    }

    private static void bridgeDockerApiVersionFromEnv() {
        if (System.getProperty("api.version") != null) {
            return;
        }
        String dockerApiVersion = System.getenv("DOCKER_API_VERSION");
        if (dockerApiVersion == null) {
            return;
        }
        String trimmed = dockerApiVersion.trim();
        if (!trimmed.isEmpty()) {
            System.setProperty("api.version", trimmed);
        }
    }

    @BeforeEach
    void truncateTables() {
        jdbcTemplate.execute("TRUNCATE TABLE plan_task_events, task_executions, task_share_links, session_messages, session_turns, agent_tasks, agent_plans, routing_decisions, workflow_drafts, workflow_definitions, agent_sessions RESTART IDENTITY CASCADE");
    }
}
