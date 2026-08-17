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
        assertThat(production).doesNotContain("8.135.19.108");
    }

    @Test
    void baotaServerHasBoundedRecoveryResourcesAndEndpointHealthCheck() throws Exception {
        String compose = Files.readString(REPOSITORY_ROOT.resolve("docker-compose.baota.yml"));

        assertThat(compose).contains("DB_SCHEMA: ${DB_SCHEMA:-plm}");
        assertThat(compose).contains("currentSchema=plm");
        assertThat(compose).contains("restart: on-failure:5");
        assertThat(compose).contains("cpus: '1.5'");
        assertThat(compose).contains("memory: 2G");
        assertThat(compose).contains("pids: 256");
        assertThat(compose).contains("/api/v1/health/db");
    }

    @Test
    void baotaExampleContainsOnlyPlaceholdersAndBootUnitIsOneShot() throws Exception {
        String example = Files.readString(REPOSITORY_ROOT.resolve(".env.baota.example"));
        String localCompose = Files.readString(REPOSITORY_ROOT.resolve("docker-compose.yml"));
        String storageOverride = Files.readString(REPOSITORY_ROOT.resolve("deploy/docker-compose.storage-override.yml"));
        String unit = Files.readString(REPOSITORY_ROOT.resolve("deploy/systemd/yuewei-plm-compose.service"));

        assertThat(example).contains("DB_SCHEMA=plm");
        assertThat(example).contains("currentSchema=plm");
        assertThat(example).doesNotContain("Postgres@123");
        assertThat(example).doesNotContain("KIRsGc1ZtwkB9chJ-Gnb1Yh-XeZ2qaF98DrbTlmBjXf3SxobX_LyeKpK3iPwKXCA");
        assertThat(localCompose).doesNotContain("KIRsGc1ZtwkB9chJ-Gnb1Yh-XeZ2qaF98DrbTlmBjXf3SxobX_LyeKpK3iPwKXCA");
        assertThat(localCompose).doesNotContain("5b6909c5.r31.cpolar.top");
        assertThat(storageOverride).contains("${PLM_STORAGE_ROOT");
        assertThat(storageOverride).contains("type: bind");
        assertThat(unit).contains("Type=oneshot");
        assertThat(unit).contains("docker compose");
        assertThat(unit).doesNotContain("Restart=always");
    }
}
