# Flyway Migration Contract

应用运行时 Flyway 只管理 `plm` schema：`default-schema=plm`、`schemas=plm`、`create-schemas=false`。生产环境通过 `DB_SCHEMA=plm` 固定该契约，`DB_URL` 必须包含 `currentSchema=plm`。

基础初始化脚本位于 `plm-database/postgres/`，仅用于受控的新库初始化或隔离恢复演练。它会创建 `plm` schema 并建立 `plm_product` 的自关联关系。

增量文件按 Flyway 版本顺序执行。不要为绕过迁移手工创建空业务表，也不要删除 `public` 中的既有表或将其 Flyway 历史当成 `plm` 的正确历史。
