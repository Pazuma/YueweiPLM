# Attachment Owner Type Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一 Product 附件的 `owner_object_type` 为数据库约束接受的 `Product`，使上传、时间轴统计、文件中心和 M5 发布门禁使用同一值。

**Architecture:** 在附件模块新增唯一领域常量 `AttachmentOwnerTypeConstants.PRODUCT`。附件服务负责写入与文件中心查询，时间轴服务负责节点附件计数，发布门禁负责必要文件分类检查，三者共同引用该常量。

**Tech Stack:** Java 17、Spring Boot 3.5、MyBatis-Plus、PostgreSQL 15、JUnit 5、Mockito、AssertJ、Maven。

## Global Constraints

- 仅修改附件所有者类型，不替换操作日志中的 `businessType="PRODUCT"`。
- 不修改数据库检查约束，不新增 Flyway 迁移。
- 不删除 `data/uploads` 中已存在的孤儿文件。
- 使用 TDD：先观察回归测试因实际值为 `PRODUCT` 而失败，再修改生产代码。
- 当前工作区已有大量用户改动，不创建分支、不提交、不回滚无关文件。

---

### Task 1: 附件上传写入统一类型

**Files:**
- Create: `src/main/java/com/yuewei/plm/module/attachment/constant/AttachmentOwnerTypeConstants.java`
- Modify: `src/main/java/com/yuewei/plm/module/attachment/service/impl/AttachmentServiceImpl.java`
- Test: `src/test/java/com/yuewei/plm/module/attachment/service/impl/AttachmentServiceImplTest.java`

**Interfaces:**
- Produces: `AttachmentOwnerTypeConstants.PRODUCT`，值固定为 `Product`。
- Consumes: `AttachmentRepository.insert(Attachment)` 的实体参数。

- [ ] **Step 1: 编写失败测试**

在上传测试中使用 `ArgumentCaptor<Attachment>` 捕获插入实体并断言：

```java
ArgumentCaptor<Attachment> attachmentCaptor = ArgumentCaptor.forClass(Attachment.class);
verify(attachmentRepository).insert(attachmentCaptor.capture());
assertThat(attachmentCaptor.getValue().getOwnerObjectType()).isEqualTo("Product");
```

- [ ] **Step 2: 运行测试确认红灯**

```powershell
$mvn = "D:\Yuewei\IntelliJ IDEA 2026.1.2\plugins\maven\lib\maven3\bin\mvn.cmd"
& $mvn "-Dtest=AttachmentServiceImplTest" test
```

预期：测试断言失败，实际值为 `PRODUCT`。

- [ ] **Step 3: 添加共享常量并修改附件服务**

```java
package com.yuewei.plm.module.attachment.constant;

public final class AttachmentOwnerTypeConstants {

    public static final String PRODUCT = "Product";

    private AttachmentOwnerTypeConstants() {
    }
}
```

删除 `AttachmentServiceImpl` 的私有 `OWNER_PRODUCT`，上传写入和 `baseQuery()` 都改为 `AttachmentOwnerTypeConstants.PRODUCT`。

- [ ] **Step 4: 运行测试确认绿灯**

执行 Task 1 Step 2 的命令，预期 `BUILD SUCCESS`。

---

### Task 2: 时间轴统计和发布门禁统一查询

**Files:**
- Modify: `src/main/java/com/yuewei/plm/module/project/service/impl/TimelineServiceImpl.java`
- Modify: `src/main/java/com/yuewei/plm/service/impl/ProductReleaseGateValidatorImpl.java`
- Test: `src/test/java/com/yuewei/plm/module/project/service/impl/TimelineServiceImplTest.java`
- Test: `src/test/java/com/yuewei/plm/service/impl/ProductReleaseGateValidatorImplTest.java`

**Interfaces:**
- Consumes: `AttachmentOwnerTypeConstants.PRODUCT`。
- Produces: 所有附件查询 Wrapper 的 owner 类型参数均为 `Product`。

- [ ] **Step 1: 编写时间轴失败测试**

捕获 `AttachmentRepository.selectCount` 的六个 Wrapper，断言每个参数集合都包含 `Product`：

```java
ArgumentCaptor<Wrapper<Attachment>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
verify(attachmentRepository, times(6)).selectCount(wrapperCaptor.capture());
assertThat(wrapperCaptor.getAllValues())
    .allSatisfy(wrapper -> assertThat(((LambdaQueryWrapper<Attachment>) wrapper)
        .getParamNameValuePairs()).containsValue("Product"));
```

- [ ] **Step 2: 编写门禁失败测试**

捕获四次附件分类查询并断言每个 Wrapper 参数集合包含 `Product`。

- [ ] **Step 3: 运行测试确认红灯**

```powershell
$mvn = "D:\Yuewei\IntelliJ IDEA 2026.1.2\plugins\maven\lib\maven3\bin\mvn.cmd"
& $mvn "-Dtest=TimelineServiceImplTest,ProductReleaseGateValidatorImplTest" test
```

预期：新增断言失败，Wrapper 参数仍包含 `PRODUCT`。

- [ ] **Step 4: 修改两处生产查询**

`TimelineServiceImpl.countDocuments` 与 `ProductReleaseGateValidatorImpl.countAttachment` 均改为：

```java
.eq(Attachment::getOwnerObjectType, AttachmentOwnerTypeConstants.PRODUCT)
```

删除发布门禁中的私有 `OWNER_OBJECT_TYPE_PRODUCT`。

- [ ] **Step 5: 运行定向测试确认绿灯**

执行 Task 2 Step 3 的命令，预期 `BUILD SUCCESS`。

---

### Task 3: 全链路验证与沉淀

**Files:**
- Modify: `docs/backend-notes/2026-07-10-PLM后端M4接入BOM工艺路线时间轴文件上传代码实现沉淀.md`
- Copy: `D:\Yuewei\资料\PLM\docs\后端-沉淀\2026-07-10-PLM后端M4接入BOM工艺路线时间轴文件上传代码实现沉淀.md`

**Interfaces:**
- Consumes: M4 时间轴附件上传、列表、文件中心和下载接口。
- Produces: 根因说明、修改文件、测试结果和 Apifox 验证步骤。

- [ ] **Step 1: 运行附件相关定向测试**

```powershell
$mvn = "D:\Yuewei\IntelliJ IDEA 2026.1.2\plugins\maven\lib\maven3\bin\mvn.cmd"
& $mvn "-Dtest=AttachmentServiceImplTest,TimelineServiceImplTest,ProductReleaseGateValidatorImplTest" test
```

预期：`BUILD SUCCESS`，三组测试全部通过。

- [ ] **Step 2: 运行全量测试**

```powershell
& $mvn test
```

预期：`BUILD SUCCESS`，失败数和错误数均为 0。

- [ ] **Step 3: 启动临时验证实例**

在 8081 端口启动并关闭开发安全开关：

```powershell
$env:SERVER_PORT = "8081"
$env:APP_SECURITY_ENABLED = "false"
& $mvn spring-boot:run
```

预期：Flyway 校验通过，Tomcat 在 8081 启动。

- [ ] **Step 4: 使用 multipart 上传并核对数据库**

请求：

```http
POST http://localhost:8081/api/v1/projects/10/timeline/MODEL_VARIANT_DIFF_DESIGN/attachments
Content-Type: multipart/form-data
```

表单字段：`file`、`fileCategory=testing`、`versionNo=V1`、`remark=M4 时间轴节点测试报告`。

数据库核对：

```sql
select attachment_id, owner_object_type, owner_object_id, timeline_node_key, file_category, storage_key
from plm.plm_attachment
where owner_object_id = 10
order by attachment_id desc;
```

预期：最新记录 `owner_object_type=Product`。

- [ ] **Step 5: 更新并复制沉淀文档**

文档需记录：问题现象、数据库约束证据、原始代码、修复逻辑、修改文件、自动化测试、真实数据库验证、Apifox 上传/查询/下载步骤、孤儿文件风险与维护建议。

- [ ] **Step 6: 最终一致性检查**

```powershell
rg -n 'OWNER_PRODUCT|OWNER_OBJECT_TYPE_PRODUCT|"PRODUCT"|AttachmentOwnerTypeConstants' src/main/java src/test/java
```

预期：附件 owner 类型路径只引用 `AttachmentOwnerTypeConstants.PRODUCT`；操作日志的 `businessType="PRODUCT"` 保留。

---

### Task 4: 下载日志旧表兼容

**Files:**
- Create: `src/main/resources/db/migration/V20260713_1600__m4_attachment_download_log_legacy_compatibility.sql`

**Interfaces:**
- Consumes: 旧版 `plm_attachment_download_log` 字段。
- Produces: 与 `AttachmentDownloadLog` 实体一致的新字段结构。

- [ ] **Step 1: 记录真实失败证据**

已通过下载接口复现：`request_id` 字段不存在，接口返回 `50001`。该失败作为迁移前红灯证据。

- [ ] **Step 2: 编写幂等兼容迁移**

迁移条件重命名四个旧字段，并使用 `add column if not exists` 补齐六个审计字段，不删除数据和约束。

- [ ] **Step 3: 启动临时实例应用迁移**

在 8081 启动应用，预期 Flyway 从 `20260713.1140` 升级到 `20260713.1600`。

- [ ] **Step 4: 重新验证下载**

下载 `attachment_id=6`，比较上传文件和下载文件 SHA-256，预期完全一致；查询下载日志表，预期新增一行审计记录。

- [ ] **Step 5: 运行全量测试**

执行 `& $mvn test`，预期失败数和错误数均为 0。

---

### Task 5: 重新生成 M4 Apifox 全链路测试文档

**Files:**
- Create: `docs/backend-notes/2026-07-13-PLM后端M4-Apifox全链路测试验收文档.md`
- Copy: `D:\Yuewei\资料\PLM\docs\后端-沉淀\2026-07-13-PLM后端M4-Apifox全链路测试验收文档.md`

**Interfaces:**
- Consumes: M4 BOM、工艺路线、时间轴附件和文件中心接口。
- Produces: 可逐项执行的 Apifox 请求、变量、请求体、预期响应、数据库核对 SQL 和故障排查说明。

- [ ] **Step 1: 从代码核对请求 DTO 与接口路径**

核对 BOM、BOM 明细、工艺路线、附件、文件中心 Controller 与 DTO，文档不沿用未经确认的旧示例。

- [ ] **Step 2: 编写完整测试顺序**

按“环境检查 → BOM → 工艺路线 → 附件上传 → 时间轴与文件中心 → 下载 → 删除 → 日志与数据库”编排。

- [ ] **Step 3: 写明每步通过标准**

每个接口包含方法、URL、Body、变量提取、预期 `code`、业务状态与数据库 SQL。

- [ ] **Step 4: 复制到外部沉淀目录**

使用 `Copy-Item` 复制工作区文档，并使用 `Get-FileHash` 核对两个文件一致。
