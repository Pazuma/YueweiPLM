# PLM 钉钉等待节点 taskId 查看与兜底查询实施沉淀

日期：2026-07-28

## 1. taskId 在哪里看

`taskId` 不是审批模板的 `processCode`，也不是审批编号 `approvalNo`。它是钉钉审批实例流转到某个等待处理节点时，对应待办任务的 ID。

在钉钉连接器里优先这样找：

1. 打开新型号审批模板的连接器动作配置。
2. 找到“出参 / 审批实例 / 待办任务 / 当前任务”相关变量。
3. 优先选择类似下面含义的动态变量：

```text
当前审批任务ID
待处理任务ID
任务ID
审批任务ID
taskId
approvalTaskId
```

4. 映射到 PLM 请求体根字段：

```json
{
  "taskId": "钉钉当前等待节点任务ID动态变量"
}
```

也可以映射为：

```json
{
  "approvalTaskId": "钉钉当前等待节点任务ID动态变量"
}
```

注意：

- `approvalInstanceId` 是审批实例 ID，用来幂等和追踪。
- `approvalNo` 是审批单编号，用来业务对账。
- `processCode` 是审批模板/流程编码，用来区分新产品线和新型号线。
- `taskId` 是当前等待节点任务 ID，用来代同意。

## 2. 本次新增兜底逻辑

如果钉钉连接器没有传 `taskId`，PLM 不再立刻失败，而是增加一层兜底查询：

```text
运模完成
-> 从入站 payload 读取 taskId / approvalTaskId / task_id / form.taskId / form.approvalTaskId
-> 如果仍为空，调用 outboundEndpoint 发起 workflow-task-lookup
-> 如果查到 taskId，继续 executeWorkflowTask 自动代同意
-> 如果仍查不到，保存 failed outbound 记录
```

## 3. 修改文件

| 文件 | 说明 |
| --- | --- |
| `plm-server/src/main/java/com/yuewei/plm/module/integration/dingtalk/service/DingTalkOutboundApprovalClient.java` | 新增 `lookupWorkflowTask(...)`，通过 `outboundEndpoint` 查询等待节点任务 ID |
| `plm-server/src/main/java/com/yuewei/plm/module/integration/dingtalk/service/DingTalkProjectCompletionReturnService.java` | 缺少 `taskId` 时先执行兜底查询，查到后继续代同意 |
| `plm-server/src/test/java/com/yuewei/plm/module/integration/dingtalk/DingTalkProjectCompletionReturnServiceTest.java` | 增加缺 `taskId` 但兜底查询成功的回归测试 |

## 4. 兜底查询请求格式

PLM 会向 `DINGTALK_OUTBOUND_ENDPOINT` 发送类似 payload：

```json
{
  "action": "workflow-task-lookup",
  "projectId": 42,
  "projectCode": "PRD-xxx",
  "projectName": "xxx",
  "productType": "model_variant",
  "model": "HR X8D",
  "nodeKey": "MODEL_VARIANT_MOLD_TRANSFER",
  "nodeName": "运模",
  "processCode": "PROC-BD65F530-F66F-46B9-8F72-0567DD68F60C",
  "processInstanceId": "钉钉审批实例ID",
  "approvalInstanceId": "钉钉审批实例ID",
  "actionerUserId": "02356802443226388318"
}
```

外部中间服务或钉钉连接器需要返回：

```json
{
  "taskId": "钉钉当前等待节点任务ID"
}
```

兼容字段：

```text
taskId
approvalTaskId
task_id
id
```

## 5. 目标效果

- 钉钉连接器传了 `taskId`：直接使用，最快最稳。
- 钉钉连接器没传 `taskId`：PLM 尝试从 `outboundEndpoint` 兜底查询。
- 兜底查到：继续自动代同意。
- 兜底没查到：PLM 项目仍保持运模完成/归档，不阻断主流程；集成记录保存为 `failed`，错误原因写清楚。

## 6. 验证记录

新增回归测试先失败：

```text
找不到 lookupWorkflowTask 方法
```

实现后运行：

```powershell
.\.codex_tmp\apache-maven-3.9.9\bin\mvn.cmd "-Dtest=DingTalkProjectCompletionReturnServiceTest,DingTalkApprovalDispatchServiceTest,DingTalkModelVariantServiceTest,MoldCodeIntakeServiceTest,TimelineActionServiceImplTest" test
```

结果：

```text
Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 7. 维护建议

1. 首选仍然是在钉钉连接器里直接映射 `taskId`。
2. `workflow-task-lookup` 兜底适合由外部中间服务处理钉钉 access token、接口签名和任务查询。
3. 如果没有配置 `DINGTALK_OUTBOUND_ENDPOINT`，兜底查询不会调用外部服务，缺 `taskId` 时仍会保存 failed 记录。
4. 生产联调时先看 outbound 记录的 `source_payload_json`，确认是否包含 `"taskIdSource":"lookup"`。
