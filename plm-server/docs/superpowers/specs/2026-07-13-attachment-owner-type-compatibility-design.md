# 附件所有者类型兼容修复设计

## 背景

时间轴节点附件上传时，物理文件能够写入本地目录，但 `plm_attachment` 元数据插入失败，接口返回 `50001`。

真实 PostgreSQL 表的 `ck_plm_attachment_owner_type` 约束只允许 `Product`，当前附件服务写入 `PRODUCT`。PostgreSQL 字符串比较区分大小写，因此该值违反检查约束。

## 修复目标

- 附件上传成功写入物理文件和数据库元数据。
- 时间轴附件列表、附件数量和文件中心能够查询到已上传附件。
- M5 发布门禁能够识别项目已有的必要附件。
- 操作日志中的业务类型 `PRODUCT` 保持不变。

## 方案比较

### 方案 A：统一附件代码为 `Product`（采用）

修改附件写入和附件查询条件，使其与现有数据库约束保持一致。

优点：改动小，不修改既有数据库约束，兼容现有领域类型命名。

### 方案 B：修改数据库约束接受 `PRODUCT`

新增 Flyway 迁移，放宽或重建检查约束。

缺点：扩大数据库语义，可能产生 `Product` 与 `PRODUCT` 两类历史数据，后续查询仍需兼容两种值。

### 方案 C：同时兼容两种值

查询使用 `IN ('Product', 'PRODUCT')`，写入固定一种值。

缺点：保留错误数据形态，增加长期维护成本；当前表中没有附件历史数据，不需要兼容双值。

## 设计范围

仅修改附件所有者类型相关路径：

- `AttachmentServiceImpl`：上传写入、时间轴附件列表、文件中心查询。
- `TimelineServiceImpl`：时间轴节点附件数量统计。
- `ProductReleaseGateValidatorImpl`：M5 发布门禁附件分类查询。
- 对应单元测试：断言写入和查询统一使用 `Product`。

以下内容不修改：

- 操作日志 `businessType="PRODUCT"`。
- 产品生命周期日志和时间轴动作日志。
- 数据库检查约束。
- 已生成但没有元数据的孤儿文件；清理需单独确认。

## 代码结构

新增附件领域常量，集中声明产品附件所有者类型 `Product`。附件服务、时间轴服务和发布门禁共同引用该常量，避免再次出现大小写不一致。

数据流：

1. 上传接口校验项目、节点、分类和文件。
2. 本地存储返回 `storageKey`、大小和校验值。
3. 附件元数据使用 `ownerObjectType=Product` 插入数据库。
4. 时间轴、文件中心和发布门禁使用同一常量查询附件。

## 错误处理

本次修复不改变接口错误码。数据库约束不再因所有者类型大小写触发；其他上传校验和文件服务异常继续使用现有业务错误处理。

物理文件先写入、元数据后插入的事务边界问题不在本次范围内。失败产生的孤儿文件单独登记，未经确认不删除。

## 测试设计

### 自动化测试

- 上传测试捕获 `AttachmentRepository.insert` 参数，断言 `ownerObjectType` 为 `Product`。
- 时间轴测试验证附件统计查询条件使用统一常量。
- 发布门禁测试验证必要附件查询使用统一常量。
- 执行附件、时间轴和发布门禁定向测试。
- 定向测试通过后执行全量测试。

### 真实环境验证

- 使用真实 PostgreSQL 启动临时端口实例。
- 使用 multipart 请求上传测试文件。
- 验证接口 `code=0`。
- 验证 `plm.plm_attachment.owner_object_type=Product`。
- 验证时间轴附件列表、文件中心查询和下载。
- 验证 M5 发布门禁能够识别对应附件分类。

## 验收标准

- 上传不再返回 `50001`。
- 本地文件与数据库元数据同时存在。
- 时间轴、文件中心和发布门禁查询结果一致。
- 附件相关测试和全量测试通过。
- 沉淀文档记录根因、修改文件及 Apifox 验证步骤。

## 设计变更记录：下载日志旧表兼容

### 变更原因

完成 `owner_object_type=Product` 修复后，真实 multipart 上传、时间轴列表和文件中心查询均成功。继续验证下载接口时，数据库报错：旧版 `plm_attachment_download_log` 缺少当前实体写入的 `request_id` 等字段。

旧表字段使用 `attachment_download_log_id`、`download_user_id`、`download_user_name`、`download_ip`；当前 M4 实体与新表定义使用 `download_log_id`、`operator_user_id`、`operator_user_name`、`client_ip`，并新增请求审计字段。原 M4 迁移只有 `create table if not exists`，无法升级已经存在的旧表。

### 采用方案

新增 Flyway 兼容迁移，执行以下幂等升级：

- 仅当旧字段存在且新字段不存在时重命名字段。
- 补充 `request_id`、`user_agent`、`created_by`、`updated_at`、`updated_by`、`deleted_flag`。
- 保留原主键、identity、外键、索引和历史下载记录。
- 新建数据库已经使用新字段时，迁移保持无操作或只补缺失字段。

### 新增验收标准

- Flyway 成功应用下载日志兼容迁移。
- 下载接口返回真实文件流，不再返回 `50001`。
- 下载文件 SHA-256 与上传文件一致。
- `plm_attachment_download_log` 成功写入下载审计记录。
- 新生成独立的 M4 Apifox 全链路测试文档，不覆盖原实施沉淀。
