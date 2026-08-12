# PLM 钉钉自动查询 taskId 并代同意实施沉淀

日期：2026-07-29
状态：已实施后端代码

## 1. 目标效果

本次实现“连接器不传 `taskId`，PLM 后端自动查询并代同意”的逻辑。

目标链路：

```text
钉钉连接器调用 PLM 出站中转接口
-> action=agree
-> PLM 根据 approvalInstanceId/processInstanceId 查询钉钉审批实例详情
-> 从 tasks 中找到 RUNNING 的待办 taskId
-> PLM 调用钉钉同意审批任务接口
-> 返回 data.status=success 或 failed
```

这样钉钉连接器中 `taskId` 可以留空，流程不再依赖钉钉连接器字段下拉是否暴露待办任务 ID。

## 2. 修改文件

### 新增文件

| 文件 | 说明 |
| --- | --- |
| `plm-server/src/main/java/com/yuewei/plm/module/integration/dingtalk/service/DingTalkAccessTokenService.java` | 获取并缓存钉钉 access token |
| `plm-server/src/main/java/com/yuewei/plm/module/integration/dingtalk/service/DingTalkOfficialApprovalClient.java` | 封装审批实例详情查询、taskId 提取、审批任务同意 |
| `plm-server/src/main/java/com/yuewei/plm/module/integration/dingtalk/service/DingTalkOfficialApprovalException.java` | 钉钉官方 API 业务异常 |
| `plm-server/src/test/java/com/yuewei/plm/module/integration/dingtalk/DingTalkOfficialApprovalClientTest.java` | taskId 提取规则测试 |

### 修改文件

| 文件 | 说明 |
| --- | --- |
| `plm-server/src/main/java/com/yuewei/plm/module/integration/dingtalk/service/DingTalkOutboundRelayService.java` | `agree` 缺少 taskId 时自动查询并继续同意；`workflow-task-lookup` 改为真实查询 |
| `plm-server/src/main/java/com/yuewei/plm/module/integration/dingtalk/vo/DingTalkOutboundRelayResultVO.java` | Builder 支持 `toBuilder`，便于失败返回补充 taskId |
| `plm-server/src/test/java/com/yuewei/plm/module/integration/dingtalk/DingTalkOutboundRelayServiceTest.java` | 更新 agree 成功路径、自动查询 taskId 测试 |
| `plm-server/src/test/java/com/yuewei/plm/module/integration/dingtalk/DingTalkOutboundRelayControllerTest.java` | 更新 Controller 测试构造方式和错误预期 |

## 3. 关键代码逻辑

### 3.1 access token 缓存

`DingTalkAccessTokenService` 调用：

```http
POST https://api.dingtalk.com/v1.0/oauth2/accessToken
```

使用配置：

```text
DINGTALK_APP_KEY
DINGTALK_APP_SECRET
```

返回的 `accessToken` 会缓存在内存中，有效期按 `expireIn - 300 秒` 计算，避免每次请求都重新获取。

### 3.2 自动查询 taskId

`DingTalkOfficialApprovalClient.lookupRunningTaskId` 调用：

```http
POST https://oapi.dingtalk.com/topapi/processinstance/get?access_token=xxx
```

请求体：

```json
{
  "process_instance_id": "审批实例ID"
}
```

提取规则：

```text
优先取 task_status/status = RUNNING 且 userid/userId = actionerUserId 的 taskid/taskId
如果没有匹配审批人，则兜底取第一条 RUNNING 任务
如果没有 RUNNING 任务，返回 DINGTALK_RUNNING_TASK_NOT_FOUND
如果没有 tasks，返回 DINGTALK_TASK_NOT_FOUND
```

### 3.3 自动同意

`DingTalkOfficialApprovalClient.agreeTask` 调用：

```http
POST https://oapi.dingtalk.com/topapi/process/instance/execute?access_token=xxx
```

请求体核心字段：

```json
{
  "request": {
    "process_instance_id": "审批实例ID",
    "task_id": "待办任务ID",
    "user_id": "审批人UserID",
    "result": "agree",
    "remark": "PLM运模已完成，自动同意"
  }
}
```

钉钉返回 `errcode=0` 时，PLM 出站中转接口返回：

```json
{
  "status": "success",
  "action": "agree",
  "taskId": "自动查到的taskId",
  "externalStatus": "success"
}
```

失败时仍保持 HTTP 200，但 `data.status=failed`，并返回具体 `errorCode/message`。

## 4. 连接器配置

请求地址：

```text
http://35487b62.r31.cpolar.top/api/v1/integrations/dingtalk/outbound
```

请求头：

```json
{
  "Authorization": "Bearer dev-token",
  "Content-Type": "application/json"
}
```

请求体字段：

| 字段 | 配置 |
| --- | --- |
| `action` | 固定值 `agree` |
| `approvalInstanceId` | 实例ID |
| `processInstanceId` | 实例ID |
| `sourceApprovalInstanceId` | 实例ID |
| `processCode` | 流程模板ID |
| `nodeKey` | 固定值 `MODEL_VARIANT_MOLD_TRANSFER` |
| `nodeName` | 固定值 `运模` |
| `productType` | 固定值 `model_variant` |
| `model` | `Modelos型号` |
| `actionerUserId` | 固定值 `02356802443226388318` |
| `result` | 固定值 `agree` |
| `remark` | 固定值 `PLM运模已完成，自动同意` |
| `taskId` | 留空 |

## 5. 后端配置

需要配置：

```text
DINGTALK_APP_KEY=钉钉企业内部应用AppKey
DINGTALK_APP_SECRET=钉钉企业内部应用AppSecret
DINGTALK_CALLBACK_TOKEN=dev-token
DINGTALK_AUTO_APPROVER_USER_ID=02356802443226388318
DINGTALK_OUTBOUND_ENDPOINT=http://35487b62.r31.cpolar.top/api/v1/integrations/dingtalk/outbound
```

注意：

- 钉钉应用需要把 PLM 服务器出口 IP 加到白名单。
- 如果在本机调试，报错中的 `request ip` 也要加入白名单。
- 生产环境不要继续使用 `dev-token`。

## 6. 好处

- 连接器不再需要映射 `taskId`。
- 自动同意逻辑收敛在 PLM 后端，便于日志、失败原因和后续重试。
- 支持钉钉返回字段大小写差异：`taskid/taskId/task_id`、`userid/userId/user_id`。
- 如果审批人不匹配但存在运行中任务，提供兜底策略，减少流程卡死。
- HTTP 仍返回 200，兼容钉钉连接器出参校验；业务失败放在 `data.status/errorCode` 中。

## 7. 验证结果

已执行：

```powershell
$env:JAVA_HOME='D:\work\Yuewei\.jdks\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\.codex_tmp\apache-maven-3.9.9\bin\mvn.cmd "-Dtest=DingTalkOfficialApprovalClientTest" "-DforkCount=0" test
.\.codex_tmp\apache-maven-3.9.9\bin\mvn.cmd "-Dtest=DingTalkOutboundRelayServiceTest,DingTalkOutboundRelayControllerTest,DingTalkProjectCompletionReturnServiceTest" "-DforkCount=0" test
```

结果：

```text
DingTalkOfficialApprovalClientTest: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
DingTalkOutboundRelayControllerTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
DingTalkOutboundRelayServiceTest: Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
DingTalkProjectCompletionReturnServiceTest: Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 8. 手工验证步骤

### 8.1 不传 taskId 调 PLM 出站接口

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://35487b62.r31.cpolar.top/api/v1/integrations/dingtalk/outbound" `
  -Headers @{ "Authorization" = "Bearer dev-token" } `
  -ContentType "application/json" `
  -Body '{"action":"agree","approvalInstanceId":"202607291410000433467","processInstanceId":"202607291410000433467","actionerUserId":"02356802443226388318","remark":"PLM运模已完成，自动同意"}'
```

预期：

```text
data.status = success
data.taskId = 自动查到的 taskId
```

如果失败，重点看：

```text
data.errorCode
data.message
```

### 8.2 常见失败判断

| 错误码 | 含义 | 处理 |
| --- | --- | --- |
| `DINGTALK_CREDENTIALS_REQUIRED` | 未配置 AppKey/AppSecret | 配置 `DINGTALK_APP_KEY/DINGTALK_APP_SECRET` |
| `DINGTALK_ACCESS_TOKEN_FAILED` | 获取 token 失败或返回异常 | 检查 appSecret、IP 白名单 |
| `DINGTALK_PROCESS_INSTANCE_QUERY_FAILED` | 查询审批实例失败 | 检查实例ID、应用权限 |
| `DINGTALK_TASK_NOT_FOUND` | 审批实例没有 tasks | 检查审批是否已到待办节点 |
| `DINGTALK_RUNNING_TASK_NOT_FOUND` | 没有运行中的待办 | 检查节点是否已审批完成 |
| `DINGTALK_TASK_ID_MISSING` | 运行中任务缺少 taskId | 检查钉钉返回结构或接口版本 |
| `DINGTALK_AGREE_FAILED` | 调用同意接口失败 | 检查审批人 userId、接口权限 |

## 9. 后续维护建议

- 后续可以增加出站失败重试接口，重试 `integration_record` 中失败的 outbound 记录。
- 如果流程存在会签/或签，应明确是否允许 fallback 到第一条 RUNNING 任务。
- 建议在生产环境把钉钉官方接口 raw response 做脱敏摘要记录，方便排查。
- access token 缓存当前为单实例内存缓存，多实例部署时可按需迁移到 Redis。
