package com.yuewei.plm.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DeploymentRecoveryContractTest {

    private static final Path REPOSITORY_ROOT = Path.of("..");

    @Test
    void flywayIsPinnedToThePlmSchemaWithoutCreatingSchemas() throws Exception {
        String application = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(application).contains("default-schema: ${DB_SCHEMA:plm}");
        assertThat(application).contains("schemas: ${DB_SCHEMA:plm}");
        assertThat(application).contains("create-schemas: false");
    }

    @Test
    void productionDatasourceRequiresDbUrlAndUsesThePlmSchemaContract() throws Exception {
        String production = Files.readString(Path.of("src/main/resources/application-prod.yml"));

        assertThat(production).contains("url: ${DB_URL}");
        assertThat(production).contains("username: ${DB_USERNAME}");
        assertThat(production).doesNotContain("jdbc:postgresql://8.");
        assertThat(production).doesNotContain("DB_USERNAME:postgres");
    }

    @Test
    void baotaServerHasBoundedRecoveryResourcesAndEndpointHealthCheck() throws Exception {
        String compose = Files.readString(REPOSITORY_ROOT.resolve("docker-compose.baota.yml"));
        String healthVo = Files.readString(Path.of(
            "src/main/java/com/yuewei/plm/module/health/vo/HealthVO.java"
        ));

        assertThat(compose).contains("DB_URL: \"${DB_URL:?DB_URL must be set}\"");
        assertThat(compose).doesNotContain("DB_URL: ${DB_URL:-");
        assertThat(compose).contains("DB_SCHEMA: plm");
        assertThat(compose).contains("DB_USERNAME: \"${DB_USERNAME:?DB_USERNAME must be set}\"");
        assertThat(compose).contains("DB_PASSWORD: \"${DB_PASSWORD:?DB_PASSWORD must be set}\"");
        assertThat(compose).contains("DB_URL must include currentSchema=plm");
        assertThat(compose).contains("restart: on-failure:5");
        assertThat(compose).contains("cpus: 1.5");
        assertThat(compose).contains("mem_limit: 2g");
        assertThat(compose).contains("pids_limit: 256");
        assertThat(compose).contains("/api/v1/health/db");
        assertThat(compose).contains("database\\\":\\\"UP");
        assertThat(healthVo).contains("String database");
    }

    @Test
    void baotaExampleContainsOnlyPlaceholdersAndBootUnitIsOneShot() throws Exception {
        String example = Files.readString(REPOSITORY_ROOT.resolve(".env.baota.example"));
        String localCompose = Files.readString(REPOSITORY_ROOT.resolve("docker-compose.yml"));
        String unit = Files.readString(REPOSITORY_ROOT.resolve("deploy/systemd/yuewei-plm-compose.service"));
        String databaseCompose = Files.readString(REPOSITORY_ROOT.resolve(
            "plm-database/postgres/docker-compose.database.yml"
        ));
        String databaseExample = Files.readString(REPOSITORY_ROOT.resolve(
            "plm-database/postgres/.env.database.example"
        ));

        assertThat(example).contains("DB_SCHEMA=plm");
        assertThat(example).contains("jdbc:postgresql://172.19.49.226:5432/plm_data_management?currentSchema=plm");
        assertThat(example).doesNotContain("DB_PASSWORD=Postgres@");
        assertThat(example).doesNotContain("DINGTALK_APP_SECRET=KIRs");
        assertThat(localCompose).doesNotContain("DINGTALK_APP_SECRET: \"${DINGTALK_APP_SECRET:-KIRs");
        assertThat(localCompose).doesNotContain("5b6909c5.r31.cpolar.top");
        assertThat(unit).contains("Type=oneshot");
        assertThat(unit).contains("WorkingDirectory=/www/wwwroot/YUEWEI/Yuewei-plm");
        assertThat(unit).contains("docker compose");
        assertThat(unit).doesNotContain("Restart=always");
        assertThat(unit).doesNotContain("--remove-orphans");
        assertThat(databaseCompose).contains(
            "172.19.49.226",
            "POSTGRES_DATA_ROOT",
            "POSTGRES_IMAGE must match the existing PGDATA major version",
            "create_host_path: false",
            "restart: unless-stopped"
        );
        assertThat(databaseCompose).contains("$${POSTGRES_USER}", "$${POSTGRES_DB}");
        assertThat(databaseCompose).doesNotContain("plm_pgdata", "plm123");
        assertThat(databaseExample).contains(
            "POSTGRES_IMAGE=postgres:17.10-alpine",
            "POSTGRES_DATA_ROOT=/data/services/postgresql",
            "DB_BIND_ADDRESS=172.19.49.226",
            "POSTGRES_DB=plm_data_management"
        );
    }

    @Test
    void databaseStorageRunbookCoversTheWholeOneTerabyteMigration() throws Exception {
        String runbook = Files.readString(REPOSITORY_ROOT.resolve(
            "docs/运维/2026-08-17-数据库服务器1TB数据盘迁移与回滚.md"
        ));
        String dockerDropIn = Files.readString(REPOSITORY_ROOT.resolve(
            "deploy/systemd/docker.service.d/10-require-data.conf"
        ));
        String containerdDropIn = Files.readString(REPOSITORY_ROOT.resolve(
            "deploy/systemd/containerd.service.d/10-require-data.conf"
        ));

        assertThat(runbook).contains("/dev/vdb2", "NTFS", "ext4", "UUID=", "/data");
        assertThat(runbook).contains(
            "/data/services/postgresql",
            "/data/datauser/files",
            "/data/backups/1panel",
            "/data/runtime/docker",
            "/data/runtime/containerd"
        );
        assertThat(runbook).contains("DockerRootDir", "root = \"/data/runtime/containerd\"");
        assertThat(runbook).contains("75%", "85%", "14 天", "快照");
        assertThat(dockerDropIn).contains("RequiresMountsFor=/data");
        assertThat(containerdDropIn).contains("RequiresMountsFor=/data");
    }

    @Test
    void productionLoginDoesNotPublishDemoCredentials() throws Exception {
        String loginView = Files.readString(REPOSITORY_ROOT.resolve("plm-web/src/views/login/LoginView.vue"));

        assertThat(loginView).contains("username: ''", "password: ''");
        assertThat(loginView).doesNotContain("plm123456", "测试账号");
    }
}
