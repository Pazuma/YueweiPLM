# PLM 数据库搭建包

本目录用于沉淀手机壳制造业 PLM 系统的 PostgreSQL 数据库设计与基础搭建脚本。

## 文件说明

- `docs/数据库设计与搭建实施方案.md`：数据库设计、实施步骤、风险和验收建议。
- `postgres/V1.0__init_schema.sql`：建表、主外键、索引、约束、表注释。
- `postgres/V1.1__init_seed_data.sql`：字典、角色、权限、编码规则、基础审批定义。
- `postgres/V1.2__erp_item_bom_alignment.sql`：对照 ERPNext Item/BOM 补充后续映射友好的预留字段。
- `postgres/V1.3__workflow_locking_alignment.sql`：补齐审批、冻结、版本追溯、模具机台关系、导入/集成状态机等增量结构。
- `postgres/V1.3__workflow_locking_seed_data.sql`：补齐 V1.3 对应的字典、权限、系统配置种子数据。
- `postgres/docker-compose.database.yml`：仅启动 PostgreSQL 的数据库验证 compose。

## 快速启动

```bash
cd postgres
cp .env.database.example .env.database
chmod 600 .env.database
# 编辑 .env.database：POSTGRES_IMAGE 必须与现有 PGDATA 的 major 版本一致；
# POSTGRES_DATA_ROOT 必须是已挂载并完成冷迁移的真实目录。
docker compose --env-file .env.database -f docker-compose.database.yml up -d
```

说明：

- `POSTGRES_IMAGE`、`POSTGRES_DATA_ROOT`、`DB_USERNAME` 和 `DB_PASSWORD` 都必须显式设置；没有生产弱口令或匿名卷回退。
- 生产默认只绑定数据库服务器私网地址 `172.19.49.226`；本地隔离验证时把 `DB_BIND_ADDRESS` 改成 `127.0.0.1`，并使用独立的空数据目录和非冲突端口。
- 已有 PGDATA 的 major 版本不能靠修改镜像标签升级；升级必须使用单独的 `pg_upgrade` 或逻辑恢复流程。
- 启动后可直接在 Docker Desktop 中看到 `plm-postgres` 容器。

首次启动时，PostgreSQL 会自动执行：

```text
V1.0__init_schema.sql
V1.1__init_seed_data.sql
V1.2__erp_item_bom_alignment.sql
V1.3__workflow_locking_alignment.sql
V1.3__workflow_locking_seed_data.sql
```

连接参数全部来自受限权限的 `.env.database`，不在仓库中保存真实密码：

```text
host: DB_BIND_ADDRESS
port: DB_PORT
database: POSTGRES_DB
username: DB_USERNAME
password: DB_PASSWORD（仅服务器保存）
```

## 验证 SQL

```bash
docker compose --env-file .env.database -f docker-compose.database.yml exec postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select count(*) from information_schema.tables where table_schema='"'"'plm'"'"';"'
docker compose --env-file .env.database -f docker-compose.database.yml exec postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select count(*) from plm.sys_dict_item;"'
docker compose --env-file .env.database -f docker-compose.database.yml exec postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select status from plm.plm_approval_instance group by status;"'
```

## 设计边界

- 当前只建设 PLM 数据库。
- 不实现 ERP/MES/WMS/CRM 正式业务闭环。
- 全系统只保留 7 个核心对象作为一级业务对象。
- BOM、样品、报价、变更、文件、质量资料均作为扩展表或附件记录承载。
- ERPNext Item/BOM 参考字段只作为映射预留，不代表本期实现 ERP 库存、采购、税务或财务流程。
