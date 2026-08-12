
## 14. 实施落地记录（2026-08-05）

### 14.1 本次实际修改范围

本次已按本方案落地代码，核心对象仍为 `Product`，未新增 SKU、BOM、文件版本等根对象，也未扩展 ERP、MES、WMS 或钉钉正式业务闭环。

#### 后端

| 文件 | 实际修改内容 |
| --- | --- |
| `plm-server/src/main/java/com/yuewei/plm/controller/dto/ProductLifecycleActionDTO.java` | 增加 `riskConfirmed`，由后端强制校验发布风险确认。 |
| `plm-server/src/main/java/com/yuewei/plm/service/vo/ProductReleaseGateCheckVO.java` | 增加 `blocking`、`confirmRequired`，区分流程硬阻断和资料风险提示。 |
| `plm-server/src/main/java/com/yuewei/plm/service/vo/ProductReleaseGateMissingItemVO.java` | 增加 `severity`，支持 `blocker`、`warning`。 |
| `plm-server/src/main/java/com/yuewei/plm/common/constant/ErrorCodeConstants.java` | 增加 `RELEASE_RISK_CONFIRM_REQUIRED=40308`。 |
| `plm-server/src/main/java/com/yuewei/plm/service/impl/ProductReleaseGateValidatorImpl.java` | BOM、工艺路线、图纸、SOP/SIP、测试资料改为 `warning`；状态、时间轴最终节点、当前节点确认等仍为 `blocker`。 |
| `plm-server/src/main/java/com/yuewei/plm/service/impl/ProductServiceImpl.java` | 发布时保留基础硬校验；存在资料风险且 `riskConfirmed` 不是 `true` 时返回 40308；确认后允许发布，并在发布日志记录风险确认和缺失项。 |
| `plm-server/src/main/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImpl.java` | 当前节点资料、跨阶段节点资料缺失不再阻断确认；改为返回 `warnings` 并写入时间轴操作日志。 |
| `plm-server/src/main/java/com/yuewei/plm/module/project/vo/TimelineActionResultVO.java` | 增加时间轴资料风险 `warnings` 字段。 |

#### 前端

| 文件 | 实际修改内容 |
| --- | --- |
| `plm-web/src/views/project/ProjectCenterView.vue` | 移除“当前节点资料上传”和“当前步骤资料上传”两个节点级上传面板；保留“资料区”项目级上传；详情头部发布接入风险预检和 `riskConfirmed`。 |
| `plm-web/src/views/project/components/ProjectReleaseGatePanel.vue` | 文案由“项目发布门禁”调整为“项目发布风险”；资料缺口弹窗确认后允许发布，基础流程阻断仍提示不可发布。 |
| `plm-web/src/api/modules/project.ts` | 补充发布风险字段、缺失项严重级别和时间轴 warnings 类型。 |
| `plm-web/src/views/project/__tests__/project-m5-release-gate-panel.spec.ts` | 更新发布风险确认和带风险发布断言。 |
| `plm-server/src/test/java/com/yuewei/plm/service/impl/ProductServiceLifecycleTest.java` | 增加资料缺失需确认、确认后发布、发布日志记录风险的测试。 |
| `plm-server/src/test/java/com/yuewei/plm/module/project/service/impl/TimelineActionServiceImplTest.java` | 更新节点资料缺失场景，验证仍可确认并返回 warnings。 |

### 14.2 实际效果

1. 产品详情“当前节点”和“项目流程”页面不再显示节点级资料上传大区块。
2. 产品线和新型号线均使用“资料区”进行项目级资料上传；工作台原有节点归属上传能力保留。
3. 缺少 BOM、工艺路线、图纸、SOP/SIP、测试资料时，发布前显示风险清单。
4. 用户确认风险后，后端接受 `riskConfirmed=true` 并允许 Product 进入 `released`。
5. 状态不为 `reviewing`、未到最终发布节点、当前节点未确认、已归档或已废弃等基础条件仍然阻断发布。
6. 节点资料缺失不再阻断节点确认或跨阶段推进，但会通过接口返回和操作日志保留提醒。
7. 发布日志包含 `riskConfirmed` 和发布时的缺失项清单，便于追溯。

### 14.3 验证结果

- 后端：`ProductServiceLifecycleTest`、`ProductReleaseGateValidatorImplTest`、`TimelineActionServiceImplTest`、`ProjectLifecycleControllerTest` 共 30 个测试全部通过。
- 前端：`project-m4-panels.spec.ts`、`project-m5-release-gate-panel.spec.ts` 共 22 个测试全部通过。
- 前端类型检查：`npm run type-check` 通过。

### 14.4 后期维护建议

1. 后续新增发布资料检查项时，明确归类为 `blocker` 或 `warning`，不要仅依赖前端判断。
2. 继续保持后端对 `riskConfirmed=true` 的强制校验，避免绕过弹窗直接发布。
3. 如果外部系统未来要求资料齐套，新增独立的集成门禁，不要复用本次宽松发布规则。
4. 定期根据资料缺失统计和发布日志，评估哪些风险项应恢复为硬阻断。
5. `ProductReleaseGateValidatorImpl` 短期保留现有命名以兼容接口，后续可在独立重构中更名为发布风险检查器。
