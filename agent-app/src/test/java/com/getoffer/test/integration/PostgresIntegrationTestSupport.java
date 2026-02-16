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
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PostgresIntegrationTestSupport {

    private static final String PROD_INIT_SQL_RELATIVE_PATH = "docs/dev-ops/postgresql/sql/01_init_database.sql";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = createPostgresContainer();

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
        jdbcTemplate.execute("TRUNCATE TABLE quality_evaluation_events, plan_task_events, task_executions, task_share_links, session_messages, session_turns, agent_tasks, agent_plans, routing_decisions, workflow_drafts, workflow_definitions, agent_sessions RESTART IDENTITY CASCADE");
    }

    private static PostgreSQLContainer<?> createPostgresContainer() {
        Path initSql = resolveProdInitSqlPath();
        return new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("agent_it")
                .withUsername("postgres")
                .withPassword("postgres")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(initSql.toAbsolutePath()),
                        "/docker-entrypoint-initdb.d/01_init_database.sql");
    }

    private static Path resolveProdInitSqlPath() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 8 && current != null; i++) {
            Path candidate = current.resolve(PROD_INIT_SQL_RELATIVE_PATH);
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate production init sql: " + PROD_INIT_SQL_RELATIVE_PATH);
    }
}
