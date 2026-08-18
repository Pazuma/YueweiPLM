# PLM 钉钉自动查询 taskId 并代同意方案沉淀

日期：2026-07-29
状态：方案沉淀，暂不修改业务代码

## 1. 背景

当前钉钉连接器已经可以成功调用 PLM 出站中转接口：

```text
POST /api/v1/integrations/dingtalk/outbound
```

但钉钉连接器流程字段里没有暴露当前待办任务的 `taskId`。如果 `agree` 动作不传 `taskId`，PLM 现有中转接口会返回：

```text
DINGTALK_TASK_ID_REQUIRED
```

业务目标是：

```text
新型号线在 PLM 中流程到“运模”
-> PLM 认为项目完成
-> PLM 自动查询钉钉当前待办 taskId
-> PLM 调用钉钉接口代审批人同意
-> 钉钉审批继续流转到后续节点
```

因此，后续应改为由 PLM 后端自动查询 `taskId`，钉钉连接器不再手工映射 `taskId`。

## 2. 目标效果

钉钉连接器请求体只需要传：

```json
{
  "action": "agree",
  "approvalInstanceId": "审批实例ID",
  "processInstanceId": "审批实例ID",
  "sourceApprovalInstanceId": "审批实例ID",
  "actionerUserId": "02356802443226388318",
  "result": "agree",
  "remark": "PLM运模已完成，自动同意"
}
```

PLM 后端自动完成：

```text
根据 approvalInstanceId/processInstanceId
-> 获取钉钉 access_token
-> 查询审批实例详情
-> 从 tasks 中找到当前 RUNNING 的待办任务
-> 取 taskId
-> 调用钉钉同意审批任务接口
-> 返回 success 或 failed
```

## 3. 建议接口链路

### 3.1 获取 access_token

使用钉钉企业内部应用的 `appKey/appSecret` 获取 access token。

```http
POST https://api.dingtalk.com/v1.0/oauth2/accessToken
```

请求体：

```json
{
  "appKey": "${DINGTALK_APP_KEY}",
  "appSecret": "${DINGTALK_APP_SECRET}"
}
```

返回：

```json
{
  "accessToken": "xxx",
  "expireIn": 7200
}
```

注意：

- access token 需要缓存，避免每次请求都重新获取。
- 钉钉应用需要配置服务器出口 IP 白名单。
- 本地调试时，报错中的 `request ip` 也要加入白名单。

### 3.2 查询审批实例详情

优先使用旧版接口，当前调试更直观：

```http
POST https://oapi.dingtalk.com/topapi/processinstance/get?access_token=${accessToken}
```

请求体：

```json
{
  "process_instance_id": "202607291410000433467"
}
```

返回中关注：

```json
{
  "process_instance": {
    "tasks": [
      {
        "taskid": "123456",
        "userid": "02356802443226388318",
        "task_status": "RUNNING"
      }
    ]
  }
}
```

取值规则：

```text
task_status = RUNNING
userid = actionerUserId
```

如果没有匹配 `userid`，可以兜底取第一条 `task_status=RUNNING` 的任务，但应记录警告。

### 3.3 调用同意审批任务接口

后续实现时需要接入钉钉审批任务同意接口。建议封装为：

```text
DingTalkOfficialApprovalClient.agreeTask(processInstanceId, taskId, actionerUserId, remark)
```

返回成功后，PLM 出站中转接口返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "success",
    "action": "agree",
    "approvalInstanceId": "202607291410000433467",
    "taskId": "123456",
    "externalStatus": "success",
    "message": "DingTalk approval task agreed"
  }
}
```

失败时仍保持 HTTP 200，并在 body 中表达业务失败：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "failed",
    "action": "agree",
    "approvalInstanceId": "202607291410000433467",
    "externalStatus": "failed",
    "errorCode": "DINGTALK_TASK_NOT_FOUND",
    "message": "未找到当前待办 taskId"
  }
}
```

## 4. 预计修改文件

| 文件 | 动作 | 说明 |
| --- | --- | --- |
| `plm-server/src/main/java/com/yuewei/plm/module/integration/dingtalk/config/DingTalkIntegrationProperties.java` | 修改 | 补充或复用 `appKey/appSecret/autoApproverUserId` 配置 |
| `plm-server/src/main/java/com/yuewei/plm/module/integration/dingtalk/service/DingTalkAccessTokenService.java` | 新增 | 获取并缓存钉钉 access token |
| `plm-server/src/main/java/com/yuewei/plm/module/integration/dingtalk/service/DingTalkOfficialApprovalClient.java` | 新增 | 封装审批实例查询、taskId 提取、审批同意调用 |
| `plm-server/src/main/java/com/yuewei/plm/module/integration/dingtalk/service/DingTalkOutboundRelayService.java` | 修改 | `agree` 缺少 taskId 时自动查询，不再直接失败 |
| `plm-server/src/main/java/com/yuewei/plm/module/integration/dingtalk/vo/DingTalkOutboundRelayResultVO.java` | 视情况修改 | 增加官方接口返回码、原始响应摘要等字段 |
| `plm-server/src/test/java/com/yuewei/plm/module/integration/dingtalk/DingTalkOutboundRelayServiceTest.java` | 修改 | 补充自动查询 taskId 和自动同意测试 |
| `plm-server/src/test/java/com/yuewei/plm/module/integration/dingtalk/DingTalkOfficialApprovalClientTest.java` | 新增 | 测试 taskId 提取规则 |

暂不建议修改数据库结构。现有 `integration_record` 已能记录出站请求、状态和错误信息。

## 5. 关键逻辑设计

### 5.1 `agree` 动作处理

```text
接收 DTO
-> 校验 approvalInstanceId/processInstanceId
-> 校验 actionerUserId
-> 如果 taskId 已传，直接使用
-> 如果 taskId 未传，调用 DingTalkOfficialApprovalClient.lookupRunningTaskId
-> 找到 taskId 后调用 agreeTask
-> 成功返回 status=success
-> 失败返回 status=failed + errorCode
```

### 5.2 taskId 查询规则

```text
tasks 为空
-> DINGTALK_TASK_NOT_FOUND

存在 userid = actionerUserId 且 task_status = RUNNING
-> 使用该 taskid

没有匹配审批人，但存在 task_status = RUNNING
-> 兜底使用第一条 RUNNING taskid
-> message 中说明 fallback

没有 RUNNING 任务
-> DINGTALK_RUNNING_TASK_NOT_FOUND
```

### 5.3 access token 缓存

建议内存缓存：

```text
accessToken
expiresAt = now + expireIn - 300 秒
```

如果未过期直接复用；过期后重新获取。

## 6. 配置项

建议后端环境变量：

```text
DINGTALK_APP_KEY=钉钉企业内部应用AppKey
DINGTALK_APP_SECRET=钉钉企业内部应用AppSecret
DINGTALK_CALLBACK_TOKEN=dev-token
DINGTALK_AUTO_APPROVER_USER_ID=02356802443226388318
```

对应应用配置：

```yaml
plm:
  integrations:
    dingtalk:
      app-key: ${DINGTALK_APP_KEY:}
      app-secret: ${DINGTALK_APP_SECRET:}
      callback-token: ${DINGTALK_CALLBACK_TOKEN:}
      auto-approver-user-id: ${DINGTALK_AUTO_APPROVER_USER_ID:}
```

## 7. 钉钉连接器配置建议

请求地址：

```text
http://replace-with-private-relay.example/api/v1/integrations/dingtalk/outbound
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

## 8. 风险点

1. 钉钉应用必须配置服务器出口 IP 白名单，否则获取 access token 会失败。
2. `actionerUserId` 必须是当前审批节点的待办人，否则同意接口可能失败。
3. 如果审批节点是多人会签/或签，需要明确取哪一个待办任务。
4. access token 过期、接口限流、钉钉网络异常都要返回业务失败并记录日志。
5. 生产环境不要使用 `dev-token`，应配置正式随机 token。

## 9. 验收方法

### 9.1 单接口验证

钉钉连接器不传 `taskId`，调用：

```json
{
  "action": "agree",
  "approvalInstanceId": "202607291410000433467",
  "processInstanceId": "202607291410000433467",
  "actionerUserId": "02356802443226388318",
  "remark": "PLM运模已完成，自动同意"
}
```

预期：

```text
PLM 自动查到 taskId
-> 调用钉钉同意成功
-> 返回 data.status=success
```

### 9.2 业务流程验证

```text
提交新型号审批
-> PLM 创建项目
-> PLM 流程到运模
-> 出站连接器触发 agree
-> 钉钉当前节点自动同意
-> 钉钉进入下一审批节点
```

## 10. 后续维护建议

- 将钉钉 API 调用统一封装在 `DingTalkOfficialApprovalClient`，避免散落在业务 Service。
- `taskId` 提取规则保持单元测试覆盖，尤其是多人审批场景。
- 出站失败记录统一查看 `integration_record.processing_status/error_message`。
- access token 缓存失败时不要影响 PLM 主流程归档，但要保留可重试记录。
- 生产环境建议增加手动重试接口，用于钉钉短暂故障后的补偿。
