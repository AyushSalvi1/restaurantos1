package com.lifeos;

import com.lifeos.entity.BaseEntity;
import com.lifeos.entity.CreatedEntity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the one class of defect that functional tests cannot see: an entity declaring a column the
 * Flyway migrations never create. Hibernate runs with {@code ddl-auto: none} for dev and test, so a
 * missing column is not detected at startup; it surfaces much later as a runtime SQL error on whichever
 * code path first reads that table. {@code focus_sessions.updated_at} was found this way, after the
 * assistant had been answering every non-document question with a 500.
 *
 * <p>Prod runs {@code ddl-auto: validate} and so fails fast on boot. This test moves that guarantee into
 * the build that runs on every change. Expectations are read from the JPA metamodel rather than a
 * hand-written list, so a new entity is covered the moment it is written.</p>
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@ActiveProfiles("test")
@DisplayName("Entity mappings match the migrated schema")
class SchemaValidationTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("every mapped column exists in the migrated schema")
    void schemaMatchesEntities() throws Exception {
        List<String> problems = new ArrayList<>();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            for (EntityType<?> type : entityManagerFactory.getMetamodel().getEntities()) {
                Class<?> javaType = type.getJavaType();
                String table = tableName(javaType);
                List<String> columns = columnsOf(metaData, table);

                if (columns.isEmpty()) {
                    problems.add(table + " is mapped by " + javaType.getSimpleName()
                            + " but the migrations never create it");
                    continue;
                }
                // Only entities that extend one of the audit superclasses may expect audit columns.
                // Permission and the role join table carry their own identity and nothing else.
                boolean audited = BaseEntity.class.isAssignableFrom(javaType)
                        || CreatedEntity.class.isAssignableFrom(javaType);
                boolean requiresUpdatedAt = BaseEntity.class.isAssignableFrom(javaType);

                if (columns.contains("id") && audited && !columns.contains("created_at")) {
                    problems.add(table + " has id but no created_at");
                }
                // A table with created_at but no updated_at means the schema and the audit superclass
                // disagree, which is exactly what broke every focus session read.
                if (columns.contains("created_at") && requiresUpdatedAt && !columns.contains("updated_at")) {
                    problems.add(table + " is mapped by " + javaType.getSimpleName()
                            + ", which writes updated_at, but the column does not exist");
                }
            }
        }

        assertThat(problems).as("migrated schema disagrees with the entity mappings")
                .isEmpty();
    }

    private String tableName(Class<?> javaType) {
        jakarta.persistence.Table table = javaType.getAnnotation(jakarta.persistence.Table.class);
        return table != null && !table.name().isBlank()
                ? table.name().toLowerCase(Locale.ROOT)
                : javaType.getSimpleName().toLowerCase(Locale.ROOT);
    }

    private List<String> columnsOf(DatabaseMetaData metaData, String table) throws Exception {
        List<String> columns = new ArrayList<>();
        for (String candidate : new String[] {table, table.toUpperCase(Locale.ROOT)}) {
            try (ResultSet rs = metaData.getColumns(null, null, candidate, "%")) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
            if (!columns.isEmpty()) {
                return columns;
            }
        }
        return columns;
    }
}