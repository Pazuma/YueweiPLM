package com.yuewei.plm.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PlmSchemaMigrationContractTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("plm")
        .withUsername("plm")
        .withPassword("test-password");

    @Test
    void initializesAndUpgradesPlmWithoutAcceptingPublicHistory() throws Exception {
        try (Connection connection = connection()) {
            ScriptUtils.executeSqlScript(
                connection,
                new FileSystemResource(Path.of("..", "plm-database", "postgres", "V1.0__init_schema.sql"))
            );
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                    create table public.flyway_schema_history (
                        installed_rank integer primary key,
                        version varchar(50),
                        description varchar(200) not null,
                        type varchar(20) not null,
                        script varchar(1000) not null,
                        checksum integer,
                        installed_by varchar(100) not null,
                        installed_on timestamp not null default current_timestamp,
                        execution_time integer not null,
                        success boolean not null
                    )
                    """);
                statement.execute("""
                    insert into public.flyway_schema_history (
                        installed_rank, version, description, type, script, installed_by, execution_time, success
                    ) values (1, '1', 'wrong public history', 'SQL', 'V1__wrong_public_history.sql', 'test', 0, true)
                    """);
            }
        }

        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema("plm")
            .schemas("plm")
            .createSchemas(false)
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .locations("classpath:db/migration-contract")
            .load();

        assertThat(flyway.migrate().success).isTrue();

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery("""
                select exists (
                    select 1
                    from pg_constraint
                    where contype = 'f'
                      and conrelid = 'plm.plm_product'::regclass
                      and confrelid = 'plm.plm_product'::regclass
                )
                """)) {
                resultSet.next();
                assertThat(resultSet.getBoolean(1)).isTrue();
            }

            try (ResultSet resultSet = statement.executeQuery("""
                select count(*)
                from plm.flyway_schema_history
                where script = 'V1__schema_history_probe.sql' and success = true
                """)) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }

            try (ResultSet resultSet = statement.executeQuery("""
                select count(*)
                from public.flyway_schema_history
                where script = 'V1__schema_history_probe.sql'
                """)) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).isZero();
            }
        }
    }

    private Connection connection() throws Exception {
        return POSTGRES.createConnection("");
    }
}
