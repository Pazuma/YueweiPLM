# PLM 后端 M4 Apifox 全链路测试验收文档

> 日期：2026-07-13  
> 仓库：`D:\Yuewei\git\YUEWEI\plm-server`  
> 测试范围：BOM、工艺路线、时间轴附件、文件中心、文件下载、删除、数据库元数据和操作日志  
> 当前 Flyway 版本：`20260713.1600`  
> 当前自动化测试：`59` 个测试，失败 `0`，错误 `0`

## 1. 文档目标

本文件是一份可直接照步骤执行的 M4 后端验收文档，不依赖旧版测试示例。

M4 的业务目标是让项目推进过程中产生的成果物挂接到 Product 项目和时间轴节点上：

- Product 项目能够创建、维护和冻结 BOM。
- Product 项目能够创建、维护和锁定工艺路线。
- 时间轴节点能够上传图纸、SOP、SIP、测试报告等附件。
- 文件中心能够按项目、节点、分类和关键字查询附件。
- 下载接口能够返回真实文件流并记录下载日志。
- 冻结后的 BOM、工艺路线不能继续修改。
- 物理文件丢失时返回明确业务错误，不能伪装成空文件。

## 2. 本次修复后的代码基线

### 2.1 附件所有者类型修复

数据库约束要求 Product 附件使用：

```text
owner_object_type=Product
```

原代码写入和查询的是大写 `PRODUCT`，会违反 PostgreSQL 检查约束。本次新增共享常量并统一以下链路：

- 附件上传元数据写入。
- 时间轴节点附件列表。
- 文件中心查询。
- 时间轴 `documentCount` 统计。
- M5 发布门禁附件分类查询。

相关代码：

```text
src/main/java/com/yuewei/plm/module/attachment/constant/AttachmentOwnerTypeConstants.java
src/main/java/com/yuewei/plm/module/attachment/service/impl/AttachmentServiceImpl.java
src/main/java/com/yuewei/plm/module/project/service/impl/TimelineServiceImpl.java
src/main/java/com/yuewei/plm/service/impl/ProductReleaseGateValidatorImpl.java
```

注意：操作日志的 `businessType=PRODUCT` 属于另一套业务标识，不需要修改。

### 2.2 下载日志旧表兼容修复

旧数据库中的下载日志字段为：

```text
attachment_download_log_id
download_user_id
download_user_name
download_ip
```

当前实体字段为：

```text
download_log_id
operator_user_id
operator_user_name
client_ip
request_id
user_agent
created_by
updated_at
updated_by
deleted_flag
```

新增迁移：

```text
src/main/resources/db/migration/V20260713_1600__m4_attachment_download_log_legacy_compatibility.sql
```

迁移只条件重命名旧字段并补齐缺失字段，不删除历史数据，不重建表。

## 3. 测试前准备

### 3.1 前置条件

- PostgreSQL 已启动，开发环境默认连接 `localhost:5433/plm`。
- 数据库中至少有一个未删除的 Product 项目。
- Java 和 IntelliJ IDEA 内置 Maven 可用。
- 测试文件扩展名在白名单内。

当前允许的扩展名：

```text
pdf,doc,docx,xls,xlsx,ppt,pptx,txt,csv,jpg,jpeg,png,zip
```

当前文件大小上限：

```text
52428800 bytes，即 50 MB
```

当前支持的文件分类：

```text
sop
sip
testing
drawing
customer_confirm
other
```

### 3.2 启动 PostgreSQL

如数据库容器尚未启动，在数据库 Compose 目录执行：

```powershell
docker compose -f D:\Yuewei\git\YUEWEI\plm-database\postgres\docker-compose.database.yml up -d
```

确认容器：

```powershell
docker ps
```

### 3.3 启动后端

PowerShell：

```powershell
cd D:\Yuewei\git\YUEWEI\plm-server

$mvn = "D:\Yuewei\IntelliJ IDEA 2026.1.2\plugins\maven\lib\maven3\bin\mvn.cmd"
$env:APP_SECURITY_ENABLED = "false"
& $mvn spring-boot:run
```

如果 `8080` 被占用，可临时使用 `8081`：

```powershell
$env:SERVER_PORT = "8081"
$env:APP_SECURITY_ENABLED = "false"
& $mvn spring-boot:run
```

启动日志必须包含：

```text
Successfully validated 8 migrations
Current version of schema "plm": 20260713.1600
Schema "plm" is up to date
Tomcat started on port 8080
Started PlmApplication
```

如果是首次应用本次兼容迁移，应看到：

```text
Migrating schema "plm" to version "20260713.1600 - m4 attachment download log legacy compatibility"
Successfully applied 1 migration
```

### 3.4 健康检查

```http
GET {{baseUrl}}/api/v1/health
```

```http
GET {{baseUrl}}/api/v1/health/db
```

通过标准：

```text
HTTP 200
code=0
GET /health：data.status=UP
GET /health/db：data.status=UP 且 data.database=UP
```

## 4. Apifox 环境配置

### 4.1 环境变量

在 Apifox 环境中新增：

| 变量 | 示例值 | 用途 |
| --- | --- | --- |
| `baseUrl` | `http://localhost:8080` | 后端地址 |
| `projectId` | `10` | Product 项目 ID |
| `currentNodeKey` | 空 | 当前时间轴节点编码 |
| `bomId` | 空 | 创建后的 BOM ID |
| `bomItemId1` | 空 | 第一条 BOM 明细 ID |
| `bomItemId2` | 空 | 第二条 BOM 明细 ID |
| `bomItemId3` | 空 | 第三条 BOM 明细 ID |
| `processId` | 空 | 工艺路线 ID |
| `attachmentId` | 空 | 附件 ID |
| `attachmentChecksum` | 空 | 上传文件 SHA-256 |

如果使用 `8081`，将 `baseUrl` 改为：

```text
http://localhost:8081
```

### 4.2 公共请求头

开发环境关闭安全校验时：

```text
X-Request-Id: m4-apifox-{{$timestamp}}
```

JSON 请求增加：

```text
Content-Type: application/json
```

不要手工给上传请求设置 `Content-Type`。选择 `form-data` 后，让 Apifox 自动生成 multipart boundary。

## 5. 准备项目和时间轴节点

### 5.1 查询项目列表

```http
GET {{baseUrl}}/api/v1/projects?page=1&size=20
```

通过标准：

```text
code=0
data.content 至少包含一个项目
```

将要验收的 `productId` 保存为 `projectId`。

### 5.2 查询项目详情

```http
GET {{baseUrl}}/api/v1/projects/{{projectId}}
```

通过标准：

```text
code=0
data.productId={{projectId}}
```

### 5.3 查询时间轴并提取当前节点

```http
GET {{baseUrl}}/api/v1/projects/{{projectId}}/timeline
```

时间轴响应没有直接提供 `currentNodeKey`。应在：

```text
data.nodes
```

中找到：

```text
nodeStatus=current
```

并将该节点的 `nodeCode` 保存为 `currentNodeKey`。

以项目 10 的已验证数据为例：

```text
productType=model_variant
currentNodeKey=MODEL_VARIANT_DIFF_DESIGN
```

Apifox 后置脚本示例：

```javascript
const response = pm.response.json();
const currentNode = response.data.nodes.find(node => node.nodeStatus === "current");
pm.environment.set("currentNodeKey", currentNode.nodeCode);
```

通过标准：

```text
code=0
currentNodeKey 非空
```

## 6. BOM 全链路测试

### 6.1 查询项目已有 BOM

```http
GET {{baseUrl}}/api/v1/projects/{{projectId}}/boms
```

通过标准：

```text
code=0
data 为数组；没有 BOM 时允许 data=[]
```

### 6.2 创建 BOM

```http
POST {{baseUrl}}/api/v1/projects/{{projectId}}/boms
```

Body：

```json
{
  "bomName": "超队3.0 M4验收BOM",
  "bomType": "ebom",
  "versionNo": "A",
  "remark": "M4 Apifox 全链路验收"
}
```

通过标准：

```text
code=0
data.productBomId 有值
data.productId={{projectId}}
data.status=draft
data.items=[]
```

保存变量：

```javascript
const response = pm.response.json();
pm.environment.set("bomId", response.data.productBomId);
```

### 6.3 添加第一条 BOM 明细

```http
POST {{baseUrl}}/api/v1/boms/{{bomId}}/items
```

```json
{
  "itemCode": "MAT-TPU-85A",
  "itemName": "TPU 原料",
  "specification": "85A 透明",
  "lineNo": 10,
  "quantity": 0.08,
  "unit": "kg",
  "lossRate": 0.02,
  "substituteFlag": 0,
  "remark": "主料"
}
```

通过标准：

```text
code=0
data.items 中存在 lineNo=10
对应明细 status=draft
```

保存该明细的 `productBomItemId` 为 `bomItemId1`。

### 6.4 添加第二条 BOM 明细

```http
POST {{baseUrl}}/api/v1/boms/{{bomId}}/items
```

```json
{
  "itemCode": "MAT-PC-BACK",
  "itemName": "PC 背板",
  "specification": "透明高光",
  "lineNo": 20,
  "quantity": 1,
  "unit": "pcs",
  "lossRate": 0.01,
  "substituteFlag": 0,
  "remark": "背板组件"
}
```

保存 `lineNo=20` 的 `productBomItemId` 为 `bomItemId2`。

### 6.5 添加第三条 BOM 明细

```http
POST {{baseUrl}}/api/v1/boms/{{bomId}}/items
```

```json
{
  "itemCode": "MAT-MAGNET-36",
  "itemName": "磁铁组件",
  "specification": "36粒磁铁环",
  "lineNo": 30,
  "quantity": 1,
  "unit": "set",
  "lossRate": 0.005,
  "substituteFlag": 0,
  "remark": "磁吸组件"
}
```

保存 `lineNo=30` 的 `productBomItemId` 为 `bomItemId3`。

### 6.6 查询 BOM 详情

```http
GET {{baseUrl}}/api/v1/boms/{{bomId}}
```

通过标准：

```text
code=0
data.productBomId={{bomId}}
data.items.length=3
lineNo 按 10、20、30 返回
```

### 6.7 修改 BOM 头信息

```http
PUT {{baseUrl}}/api/v1/boms/{{bomId}}
```

```json
{
  "bomName": "超队3.0 M4验收BOM-修订",
  "bomType": "ebom",
  "versionNo": "A",
  "remark": "冻结前完成维护"
}
```

通过标准：

```text
code=0
data.bomName=超队3.0 M4验收BOM-修订
data.status=draft
```

### 6.8 修改 BOM 明细

```http
PUT {{baseUrl}}/api/v1/boms/{{bomId}}/items/{{bomItemId1}}
```

```json
{
  "itemCode": "MAT-TPU-85A",
  "itemName": "TPU 原料",
  "specification": "85A 高透",
  "lineNo": 10,
  "quantity": 0.085,
  "unit": "kg",
  "lossRate": 0.025,
  "substituteFlag": 0,
  "remark": "验收修改用量"
}
```

通过标准：

```text
code=0
lineNo=10 的 quantity=0.085
```

### 6.9 验证重复行号

再次添加 `lineNo=10` 的明细。

通过标准：

```text
code=40901
message 明确表示同一 BOM 下行号不能重复
```

### 6.10 冻结 BOM

```http
POST {{baseUrl}}/api/v1/boms/{{bomId}}/freeze
```

通过标准：

```text
code=0
data.status=frozen
data.frozenAt 有值
```

### 6.11 验证冻结后不可修改

任选以下请求重新执行：

```http
PUT    {{baseUrl}}/api/v1/boms/{{bomId}}
POST   {{baseUrl}}/api/v1/boms/{{bomId}}/items
PUT    {{baseUrl}}/api/v1/boms/{{bomId}}/items/{{bomItemId1}}
DELETE {{baseUrl}}/api/v1/boms/{{bomId}}/items/{{bomItemId2}}
```

通过标准：

```text
code=40301
message 包含“BOM已冻结，不能修改”
```

## 7. 工艺路线全链路测试

### 7.1 查询项目已有工艺路线

```http
GET {{baseUrl}}/api/v1/projects/{{projectId}}/process-routes
```

通过标准：

```text
code=0
data 为数组；没有路线时允许 data=[]
```

### 7.2 创建三工序路线

```http
POST {{baseUrl}}/api/v1/projects/{{projectId}}/process-routes
```

```json
{
  "processName": "超队3.0 M4验收工艺路线",
  "versionNo": "A",
  "remark": "M4 Apifox 工艺路线验收",
  "operations": [
    {
      "sequenceNo": 10,
      "processName": "注塑成型",
      "processParamJson": "{\"temperature\":80,\"pressure\":0.5}",
      "standardTimeMins": 15,
      "qualityRequirement": "外观无缩水、无明显披锋",
      "remark": "首道工序"
    },
    {
      "sequenceNo": 20,
      "processName": "磁铁装配",
      "processParamJson": "{\"magnetCount\":36}",
      "standardTimeMins": 8,
      "qualityRequirement": "磁铁方向一致，磁吸力满足规格",
      "remark": "装配工序"
    },
    {
      "sequenceNo": 30,
      "processName": "功能测试",
      "processParamJson": "{\"sampleSize\":5}",
      "standardTimeMins": 5,
      "qualityRequirement": "磁吸、按键和孔位全部通过",
      "remark": "质量确认"
    }
  ]
}
```

`processParamJson` 是 JSON 字符串，不是嵌套 JSON 对象。

通过标准：

```text
code=0
data.processId 有值
data.productId={{projectId}}
data.processType=routing
data.status=draft
data.operations.length=3
operations 按 sequenceNo 10、20、30 返回
```

保存变量：

```javascript
const response = pm.response.json();
pm.environment.set("processId", response.data.processId);
```

### 7.3 查询工艺路线详情

```http
GET {{baseUrl}}/api/v1/process-routes/{{processId}}
```

通过标准：

```text
code=0
data.processId={{processId}}
data.operations.length=3
processParamJson 能正常读回
```

### 7.4 修改工艺路线和质量要求

更新接口需要提交完整路线和完整工序列表：

```http
PUT {{baseUrl}}/api/v1/process-routes/{{processId}}
```

```json
{
  "processName": "超队3.0 M4验收工艺路线-修订",
  "versionNo": "A",
  "remark": "冻结前调整参数",
  "operations": [
    {
      "sequenceNo": 10,
      "processName": "注塑成型",
      "processParamJson": "{\"temperature\":82,\"pressure\":0.55}",
      "standardTimeMins": 16,
      "qualityRequirement": "外观无缩水、无披锋、尺寸符合图纸",
      "remark": "已调整参数"
    },
    {
      "sequenceNo": 20,
      "processName": "磁铁装配",
      "processParamJson": "{\"magnetCount\":36,\"polarityCheck\":true}",
      "standardTimeMins": 8,
      "qualityRequirement": "磁铁方向一致，磁吸力满足规格",
      "remark": "增加极性检查"
    },
    {
      "sequenceNo": 30,
      "processName": "功能测试",
      "processParamJson": "{\"sampleSize\":8}",
      "standardTimeMins": 6,
      "qualityRequirement": "磁吸、按键、孔位和外观全部通过",
      "remark": "提高抽检数量"
    }
  ]
}
```

通过标准：

```text
code=0
data.processName 包含“修订”
data.operations[0].processParamJson 包含 temperature=82
data.operations[0].qualityRequirement 包含“尺寸符合图纸”
```

### 7.5 验证非法 JSON

将任一工序改为：

```json
{
  "processParamJson": "{temperature:82}"
}
```

通过标准：

```text
code=40001
message 包含“工艺参数必须是合法JSON”
```

### 7.6 验证重复工序顺序

将两个工序的 `sequenceNo` 都设置为 `10`。

通过标准：

```text
code=40901
message 包含“同一工艺路线下工序顺序不能重复”
```

### 7.7 冻结工艺路线

```http
POST {{baseUrl}}/api/v1/process-routes/{{processId}}/freeze
```

通过标准：

```text
code=0
data.status=locked
data.frozenAt 有值
data.operations[*].status=locked
```

### 7.8 验证锁定后不可修改

重新执行：

```http
PUT {{baseUrl}}/api/v1/process-routes/{{processId}}
```

通过标准：

```text
code=40301
message 包含“工艺路线已锁定，不能修改”
```

## 8. 时间轴附件全链路测试

### 8.1 准备测试文件

准备一个白名单类型文件，例如：

```text
M4-时间轴节点测试报告.txt
```

文件内容建议包含：

```text
项目 ID
节点编码
测试日期
测试结论
```

### 8.2 上传当前节点附件

```http
POST {{baseUrl}}/api/v1/projects/{{projectId}}/timeline/{{currentNodeKey}}/attachments
```

Apifox Body 选择 `form-data`：

| Key | 类型 | 值 |
| --- | --- | --- |
| `file` | File | 选择本机测试文件 |
| `fileCategory` | Text | `testing` |
| `versionNo` | Text | `V1` |
| `remark` | Text | `M4 时间轴节点测试报告` |

不要手工填写 multipart `Content-Type`。

通过标准：

```text
HTTP 200
code=0
data.attachmentId 有值
data.ownerObjectType=Product
data.ownerObjectId={{projectId}}
data.timelineNodeKey={{currentNodeKey}}
data.fileCategory=testing
data.status=draft
data.storageType=local
data.storageKey 有值
data.checksum 为 64 位 SHA-256
```

保存变量：

```javascript
const response = pm.response.json();
pm.environment.set("attachmentId", response.data.attachmentId);
pm.environment.set("attachmentChecksum", response.data.checksum);
```

### 8.3 验证本机真实文件

物理文件位置：

```text
D:\Yuewei\git\YUEWEI\plm-server\data\uploads\{{storageKey}}
```

通过标准：

```text
文件真实存在
文件大小与 data.fileSize 一致
```

### 8.4 查询节点附件

```http
GET {{baseUrl}}/api/v1/projects/{{projectId}}/timeline/{{currentNodeKey}}/attachments
```

通过标准：

```text
code=0
data 中能找到 {{attachmentId}}
对应 ownerObjectType=Product
```

### 8.5 验证时间轴附件数量

```http
GET {{baseUrl}}/api/v1/projects/{{projectId}}/timeline
```

找到 `nodeCode={{currentNodeKey}}` 的节点。

通过标准：

```text
documentCount 至少增加 1
```

### 8.6 查询附件详情

```http
GET {{baseUrl}}/api/v1/attachments/{{attachmentId}}
```

通过标准：

```text
code=0
data.attachmentId={{attachmentId}}
data.ownerObjectType=Product
data.storageKey 与上传响应一致
```

## 9. 文件中心查询测试

### 9.1 按项目查询

```http
GET {{baseUrl}}/api/v1/file-center/attachments?projectId={{projectId}}&page=1&size=20
```

### 9.2 按项目、节点和分类查询

```http
GET {{baseUrl}}/api/v1/file-center/attachments?projectId={{projectId}}&nodeKey={{currentNodeKey}}&fileCategory=testing&page=1&size=20
```

通过标准：

```text
code=0
data.content 中能找到 {{attachmentId}}
data.totalElements>=1
```

### 9.3 按关键字查询

```http
GET {{baseUrl}}/api/v1/file-center/attachments?projectId={{projectId}}&keyword=M4&page=1&size=20
```

关键字会匹配文件名或备注。

### 9.4 无结果场景

```http
GET {{baseUrl}}/api/v1/file-center/attachments?projectId={{projectId}}&keyword=绝对不存在的文件关键字&page=1&size=20
```

通过标准：

```text
code=0
data.content=[]
data.totalElements=0
```

无结果是成功空结果，不能返回 `50001`。

## 10. 下载与下载日志测试

### 10.1 下载真实文件

```http
GET {{baseUrl}}/api/v1/attachments/{{attachmentId}}/download
```

Apifox 使用“下载文件”或“保存响应”功能。

通过标准：

```text
HTTP 200
响应不是 ResponseVO JSON
Content-Disposition 包含原始文件名
Content-Type 与上传文件类型一致或为 application/octet-stream
下载文件可以正常打开
```

### 10.2 校验 SHA-256

PowerShell：

```powershell
Get-FileHash "<上传源文件路径>" -Algorithm SHA256
Get-FileHash "<下载文件路径>" -Algorithm SHA256
```

通过标准：两个 SHA-256 完全一致，并与上传响应的 `data.checksum` 一致。

本次真实验证结果：

```text
CE4DBF6D90433D5DDB611E27E39012C23FB40152B2BF69B00DAA111E980E93B5
```

上传和下载文件哈希一致。

### 10.3 查询下载日志

```sql
select download_log_id,
       attachment_id,
       operator_user_id,
       operator_user_name,
       request_id,
       client_ip,
       user_agent,
       created_at
from plm.plm_attachment_download_log
where attachment_id = {{attachmentId}}
order by download_log_id desc;
```

通过标准：下载一次新增一条记录，`request_id` 和 `created_at` 有值。

## 11. 附件删除测试

删除是软删除，不删除本机物理文件。

```http
DELETE {{baseUrl}}/api/v1/attachments/{{attachmentId}}
```

通过标准：

```text
code=0
data=null
```

删除后重新执行：

```http
GET {{baseUrl}}/api/v1/attachments/{{attachmentId}}
```

通过标准：

```text
code=40402
message 包含“附件不存在”
```

节点附件列表和文件中心也不再返回该附件。

建议在完成下载和数据库日志核对后再执行删除测试。

## 12. 物理文件丢失异常测试

本步骤只允许在测试环境执行，并使用专门创建的可丢弃附件。

1. 上传一份专用测试附件。
2. 记录返回的 `storageKey`。
3. 确认目标路径位于：

```text
D:\Yuewei\git\YUEWEI\plm-server\data\uploads
```

4. 暂时将物理文件改名，不要删除数据库元数据。
5. 调用下载接口。

```http
GET {{baseUrl}}/api/v1/attachments/{{attachmentId}}/download
```

通过标准：

```text
code=50003
message 明确表示附件元数据存在，但本机文件已丢失
```

测试结束后恢复原文件名。

## 13. 数据库核对 SQL

### 13.1 BOM 头

```sql
select product_bom_id,
       product_id,
       bom_code,
       bom_name,
       bom_type,
       version_no,
       status,
       frozen_at,
       frozen_by,
       deleted_flag
from plm.plm_product_bom
where product_bom_id = {{bomId}};
```

### 13.2 BOM 明细

```sql
select product_bom_item_id,
       product_bom_id,
       item_code,
       item_name,
       specification,
       line_no,
       quantity,
       uom_code,
       loss_rate,
       status,
       deleted_flag
from plm.plm_product_bom_item
where product_bom_id = {{bomId}}
order by line_no;
```

通过标准：BOM 冻结后头状态为 `frozen`，有效明细至少 2 至 3 条。

### 13.3 工艺路线和工序

```sql
select process_id,
       parent_process_id,
       product_id,
       process_code,
       process_name,
       process_type,
       sequence_no,
       process_param_json,
       quality_requirement,
       status,
       deleted_flag
from plm.plm_process
where process_id = {{processId}}
   or parent_process_id = {{processId}}
order by parent_process_id nulls first, sequence_no;
```

通过标准：路线 `process_type=routing`，工序 `process_type=operation`；冻结后全部为 `locked`。

### 13.4 附件元数据

```sql
select attachment_id,
       owner_object_type,
       owner_object_id,
       timeline_node_key,
       file_category,
       original_file_name,
       file_size,
       checksum,
       storage_type,
       storage_key,
       version_no,
       status,
       deleted_flag
from plm.plm_attachment
where attachment_id = {{attachmentId}};
```

通过标准：

```text
owner_object_type=Product
owner_object_id={{projectId}}
timeline_node_key={{currentNodeKey}}
deleted_flag=0；执行软删除后为 1
```

### 13.5 操作日志

```sql
select log_id,
       action,
       business_type,
       business_id,
       result,
       request_id,
       created_at
from plm.plm_operation_log
where action in (
    'BOM_CREATE',
    'BOM_UPDATE',
    'BOM_ITEM_CREATE',
    'BOM_ITEM_UPDATE',
    'BOM_ITEM_DELETE',
    'BOM_FREEZE',
    'PROCESS_ROUTE_CREATE',
    'PROCESS_ROUTE_UPDATE',
    'PROCESS_ROUTE_FREEZE',
    'ATTACHMENT_UPLOAD',
    'ATTACHMENT_DOWNLOAD',
    'ATTACHMENT_DELETE'
)
order by log_id desc;
```

通过标准：关键动作均有日志，成功动作 `result=SUCCESS` 或对应成功值。

### 13.6 Flyway 历史

```sql
select installed_rank,
       version,
       description,
       success,
       installed_on
from plm.flyway_schema_history
order by installed_rank;
```

必须包含：

```text
20260710.1000  m4 bom process attachment
20260713.1140  m4 bom legacy compatibility
20260713.1600  m4 attachment download log legacy compatibility
```

## 14. 常见错误与排查

| 现象 | 原因 | 处理方法 |
| --- | --- | --- |
| `mvn` 无法识别 | Maven 未加入 PATH | 使用 IntelliJ IDEA 内置 `mvn.cmd` 完整路径 |
| PowerShell 提示参数列表缺少参量 | `-Dtest` 中逗号被 PowerShell 解析 | 将整个参数写成引号：`"-Dtest=A,B"` |
| 启动只显示 Maven `exit code: 1` | 真正异常在前面的 Spring 日志 | 向上查找第一个 `Caused by`、`APPLICATION FAILED` 或数据库错误 |
| Flyway 版本解析为科学计数法 | YAML 中 baseline 版本未加引号 | 使用 `baseline-version: "20260703.0000"` |
| 创建 BOM 返回 `50001` | 旧 BOM 表必填字段或约束未兼容 | 确认 `20260713.1140` 迁移成功 |
| 创建工艺路线返回 `50001` | JSON 字符串按 VARCHAR 写入 jsonb | 确认 `PostgresJsonbStringTypeHandler` 已加载并重启服务 |
| 上传附件返回 `50001` | 代码写入 `owner_object_type=PRODUCT` | 确认当前代码返回 `ownerObjectType=Product` |
| 上传成功但列表查不到 | 写入和查询使用不同 owner 类型 | 确认三处查询统一使用 `AttachmentOwnerTypeConstants.PRODUCT` |
| 下载返回 `50001` 且日志提示 `request_id` 不存在 | 下载日志仍是旧表结构 | 确认 `20260713.1600` 迁移成功 |
| 文件中心无结果 | 查询条件不匹配或附件已软删除 | 先只传 `projectId`，再逐步增加节点、分类、关键字 |
| 下载返回 `50003` | 元数据存在但物理文件丢失 | 检查 `data/uploads/{{storageKey}}` |
| 冻结后修改返回 `40301` | 正常冻结门禁 | 属于验收通过结果，不要修改数据库绕过 |

## 15. 自动化测试命令

### 15.1 M4 与发布门禁定向测试

```powershell
cd D:\Yuewei\git\YUEWEI\plm-server
$mvn = "D:\Yuewei\IntelliJ IDEA 2026.1.2\plugins\maven\lib\maven3\bin\mvn.cmd"
& $mvn "-Dtest=ProductBomServiceImplTest,ProcessRouteServiceImplTest,AttachmentServiceImplTest,TimelineServiceImplTest,ProductReleaseGateValidatorImplTest" test
```

注意：多个测试类必须放在同一个带引号的 `-Dtest` 参数中。

### 15.2 全量测试

```powershell
& $mvn test
```

2026-07-13 本次修复后的结果：

```text
Tests run: 59, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 16. 本次真实验证记录

真实 PostgreSQL：

```text
jdbc:postgresql://localhost:5433/plm
PostgreSQL 15
```

已验证：

```text
Flyway 20260713.1600 应用成功
POST 时间轴附件上传：code=0
plm_attachment.owner_object_type=Product
节点附件列表：可查询
文件中心项目/节点/分类查询：可查询
GET 下载接口：HTTP 200
上传与下载文件 SHA-256：一致
plm_attachment_download_log：成功新增下载日志
全量自动化测试：59/59 通过
```

验证使用的数据：

```text
projectId=10
currentNodeKey=MODEL_VARIANT_DIFF_DESIGN
attachmentId=6
fileCategory=testing
```

该数据仅作为本次开发验证记录。后续 Apifox 验收应使用新建的 BOM、工艺路线和附件，避免与历史数据混淆。

## 17. 完整验收清单

- [ ] 后端正常启动，Flyway 当前版本为 `20260713.1600`。
- [ ] 健康检查和数据库健康检查均为 `UP`。
- [ ] 项目能够创建 BOM。
- [ ] BOM 能添加至少 3 条明细。
- [ ] BOM 头和明细能够修改。
- [ ] 重复 BOM 行号返回 `40901`。
- [ ] BOM 冻结后状态为 `frozen`。
- [ ] BOM 冻结后修改返回 `40301`。
- [ ] 项目能够创建包含 3 道工序的工艺路线。
- [ ] 工序顺序、JSON 参数、标准时间和质量要求能够维护。
- [ ] 非法 JSON 返回 `40001`。
- [ ] 重复工序顺序返回 `40901`。
- [ ] 工艺路线冻结后路线和工序均为 `locked`。
- [ ] 工艺路线锁定后修改返回 `40301`。
- [ ] 当前时间轴节点能够上传真实文件。
- [ ] 上传响应 `ownerObjectType=Product`。
- [ ] 本机 `data/uploads` 中能看到真实文件。
- [ ] `plm_attachment` 中能看到元数据。
- [ ] 时间轴节点附件列表能查到文件。
- [ ] 时间轴 `documentCount` 正确增加。
- [ ] 文件中心能按项目、节点、分类和关键字查询。
- [ ] 文件中心无结果时返回成功空分页。
- [ ] 下载接口返回真实文件流。
- [ ] 上传和下载文件 SHA-256 一致。
- [ ] 下载动作写入 `plm_attachment_download_log`。
- [ ] 物理文件丢失时返回 `50003`。
- [ ] 删除附件后元数据软删除，查询返回 `40402`。
- [ ] BOM、工艺、附件关键动作写入 `plm_operation_log`。
- [ ] M4/M5 定向测试通过。
- [ ] 全量 Maven 测试通过。

## 18. 后续维护建议

1. 附件所有者类型统一从 `AttachmentOwnerTypeConstants` 引用，不再散落硬编码字符串。
2. Flyway 已执行的迁移文件不得修改；后续数据库变化继续新增更高版本迁移。
3. 文件中心无结果返回成功空分页，存储失败和文件丢失必须返回错误码，不能吞掉异常。
4. 当前本地存储通过 `StorageClient` 抽象；后续切换 MinIO/S3 时保持 Controller 和 Service 合同不变。
5. 当前删除是元数据软删除，物理文件清理策略需要单独设计保留期和审计规则。
6. 下载日志属于审计数据，不应随附件软删除而删除。
7. M5 发布门禁查询附件时必须继续使用 `owner_object_type=Product`，否则会把已有资料误判为缺失。
