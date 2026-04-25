package com.iems.projectservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting document permission migration...");

        migrateTable("role_permissions", "role_id");
        migrateTable("member_permissions", "account_id");

        log.info("Document permission migration completed.");
    }

    private void migrateTable(String tableName, String ownerColumn) {
        log.info("Migrating table: {}", tableName);

        String idMatch = (tableName.equals("role_permissions")) 
            ? String.format("t2.%s = %s.%s", ownerColumn, tableName, ownerColumn)
            : String.format("t2.%s = %s.%s AND t2.project_id = %s.project_id", ownerColumn, tableName, ownerColumn, tableName, tableName);

        try {
            // 1. Migrate READ/DOWNLOAD to VIEW
            jdbcTemplate.execute(String.format(
                "UPDATE %s SET permission = 'DOCUMENT_VIEW' " +
                "WHERE permission IN ('DOCUMENT_READ', 'DOCUMENT_DOWNLOAD') " +
                "AND NOT EXISTS (SELECT 1 FROM %s t2 WHERE %s AND t2.permission = 'DOCUMENT_VIEW')",
                tableName, tableName, idMatch
            ));

            jdbcTemplate.execute(String.format(
                "DELETE FROM %s WHERE permission IN ('DOCUMENT_READ', 'DOCUMENT_DOWNLOAD')",
                tableName
            ));

            // 2. Migrate CREATE/UPDATE/DELETE to MODIFY
            jdbcTemplate.execute(String.format(
                "UPDATE %s SET permission = 'DOCUMENT_MODIFY' " +
                "WHERE permission IN ('DOCUMENT_CREATE', 'DOCUMENT_UPDATE', 'DOCUMENT_DELETE') " +
                "AND NOT EXISTS (SELECT 1 FROM %s t2 WHERE %s AND t2.permission = 'DOCUMENT_MODIFY')",
                tableName, tableName, idMatch
            ));

            jdbcTemplate.execute(String.format(
                "DELETE FROM %s WHERE permission IN ('DOCUMENT_CREATE', 'DOCUMENT_UPDATE', 'DOCUMENT_DELETE')",
                tableName
            ));
        } catch (Exception e) {
            log.error("Error migrating table {}: {}", tableName, e.getMessage());
        }
    }

}
