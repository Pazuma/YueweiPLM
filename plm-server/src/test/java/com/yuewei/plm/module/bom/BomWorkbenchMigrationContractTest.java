package com.yuewei.plm.module.bom;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BomWorkbenchMigrationContractTest {

    @Test
    void migrationDefinesRouteColorCostAndImportExtensions() throws Exception {
        Path migrationPath = Path.of(
            "src/main/resources/db/migration/V20260718_1100__bom_workbench_route_cost_import.sql"
        );

        assertThat(Files.exists(migrationPath)).isTrue();

        String migration = Files.readString(migrationPath).toLowerCase();
        assertThat(migration).contains("alter table if exists plm_product_bom");
        assertThat(migration).contains("bom_scope");
        assertThat(migration).contains("test_total_cost");
        assertThat(migration).contains("create table if not exists plm_product_bom_route");
        assertThat(migration).contains("create table if not exists plm_product_bom_route_color");
        assertThat(migration).contains("create table if not exists plm_product_bom_cost_snapshot");
        assertThat(migration).contains("create table if not exists plm_product_bom_import_batch");
        assertThat(migration).contains("product_bom_id");
        assertThat(migration).contains("process_id");
        assertThat(migration).contains("color_name");
        assertThat(migration).contains("currency_code");
        assertThat(migration).contains("import_token");
        assertThat(migration).contains("where status = 'active' and deleted_flag = 0");
        assertThat(migration).contains("created_at");
        assertThat(migration).contains("created_by");
        assertThat(migration).contains("updated_at");
        assertThat(migration).contains("updated_by");
        assertThat(migration).contains("deleted_flag");
    }
}
