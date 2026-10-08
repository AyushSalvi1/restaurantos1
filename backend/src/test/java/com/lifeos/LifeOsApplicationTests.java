package com.lifeos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the whole application context starts: every bean resolves, the Flyway migrations apply to
 * the configured database and Hibernate accepts the entity mappings. This is the broadest single
 * check available and catches wiring, schema and mapping regressions in one place.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Application context")
class LifeOsApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("starts with every bean resolvable")
    void contextLoads() {
        assertThat(context).isNotNull();
        assertThat(context.getBean(com.lifeos.controller.AuthController.class)).isNotNull();
        assertThat(context.getBean(com.lifeos.controller.TaskController.class)).isNotNull();
        assertThat(context.getBean(com.lifeos.controller.AiController.class)).isNotNull();
        assertThat(context.getBean(com.lifeos.controller.AdminController.class)).isNotNull();
        assertThat(context.getBean(com.lifeos.analytics.BalanceScoreService.class)).isNotNull();
        assertThat(context.getBean(com.lifeos.analytics.InsightService.class)).isNotNull();
        assertThat(context.getBean(com.lifeos.analytics.PredictionService.class)).isNotNull();
        assertThat(context.getBean(com.lifeos.rag.RagService.class)).isNotNull();
        assertThat(context.getBean(com.lifeos.scheduler.LifecycleScheduler.class)).isNotNull();
    }

    @Test
    @DisplayName("applies every migration on a clean database")
    void migrationsApplied() throws Exception {
        try (var connection = context.getBean(javax.sql.DataSource.class).getConnection();
             var statement = connection.createStatement()) {
            var tables = new java.util.HashSet<String>();
            try (var rows = statement.executeQuery(
                    "select table_name from information_schema.tables where table_schema = 'PUBLIC'")) {
                while (rows.next()) {
                    tables.add(rows.getString(1).toLowerCase(java.util.Locale.ROOT));
                }
            }
            assertThat(tables)
                    .contains("users", "tasks", "goals", "habits", "productivity_metrics",
                            "knowledge_documents", "insights", "predictions", "ai_messages");
        }
    }
}
