# 鉴权上下文与 HTTP 状态码修复设计

## 背景

工作台点击“返回上一步”时，接口返回：

- HTTP 状态码：400 Bad Request
- 业务码：40101
- 提示：未登录或登录已失效

只读访问 `/api/v1/auth/profile` 可以稳定复现相同结果。现有鉴权过滤器在 token 无效时会直接返回 HTTP 401；因此当前响应来自业务服务读取不到 `CurrentUserContext` 后抛出的 `BusinessException(40101)`，随后被全局异常处理器统一映射成 HTTP 400。

## 目标

1. 本地运行环境恢复正常鉴权，明确启用 `APP_SECURITY_ENABLED=true`。
2. 用户重新登录后，确认、退回等需要审计操作人的写操作能够获取当前用户。
3. 业务码 `40101` 始终对应 HTTP 401，业务码 `40103` 始终对应 HTTP 403。
4. 其他普通业务异常继续返回 HTTP 400。

## 非目标

- 不在关闭鉴权时注入固定开发用户。
- 不移除服务层对当前用户的防御性检查。
- 不修改时间轴退回、确认或推进的业务规则。
- 不修改 token 的生成、存储或有效期策略。

## 设计

### 运行配置

检查当前 8080 后端的启动脚本或环境变量，移除 `APP_SECURITY_ENABLED=false` 覆盖，或将其显式设置为 `true`。重启后端后，安全过滤器必须校验 Bearer Token，并在请求期间填充 `CurrentUserContext`。

前端重新登录并更新 `localStorage.plm_token`。旧 token 不作为修复的一部分继续复用。

### 异常状态映射

调整 `GlobalExceptionHandler` 对 `BusinessException` 的响应状态选择逻辑：

- `ErrorCodeConstants.UNAUTHORIZED` (`40101`) -> HTTP 401
- `ErrorCodeConstants.FORBIDDEN` (`40103`) -> HTTP 403
- 其他业务码 -> HTTP 400

响应体结构保持不变，继续返回原始业务码、消息、数据、请求 ID 和时间戳。

### 请求数据流

1. 前端 Axios 请求拦截器从 `localStorage.plm_token` 读取 token，并发送 `Authorization: Bearer <token>`。
2. `SimpleTokenAuthenticationFilter` 校验 token。
3. token 有效时，过滤器设置 Spring Security 上下文和 `CurrentUserContext`，再调用控制器与业务服务。
4. 时间轴服务从 `CurrentUserContext` 取得操作人，执行退回并写审计日志。
5. token 无效或缺失时，过滤器直接返回 HTTP 401，不进入业务服务。
6. 若其他调用路径在业务层触发 `BusinessException(40101/40103)`，全局异常处理器返回与业务码一致的 HTTP 状态。

## 错误处理

- 无 token、token 无效或 token 过期：HTTP 401，`code=40101`。
- 已认证但无权限：HTTP 403，`code=40103`。
- 退回原因缺失、非法节点转换等业务校验失败：HTTP 400，并保留对应业务码和消息。
- 不用空响应或普通 400 掩盖鉴权失败。

## 测试与验收

### 自动化测试

- 全局异常处理器将 `BusinessException(40101)` 映射为 HTTP 401。
- 全局异常处理器将 `BusinessException(40103)` 映射为 HTTP 403。
- 普通 `BusinessException` 仍映射为 HTTP 400。
- 安全过滤器在 token 缺失或无效时返回 HTTP 401 和 `code=40101`。
- 时间轴退回服务在存在当前用户时能够完成退回并记录操作人。

### 联调验收

1. 以 `APP_SECURITY_ENABLED=true` 重启 8080 后端。
2. 前端重新登录。
3. `/api/v1/auth/profile` 返回 HTTP 200。
4. 对项目 9 执行“返回上一步”，接口不再返回 `40101`；若满足业务条件，应成功退回。
5. 清除或篡改 token 后重试，接口返回 HTTP 401，而不是 HTTP 400。

## 风险与回退

启用鉴权后，依赖旧的无鉴权启动方式访问接口会收到 HTTP 401，这是预期行为。若联调受阻，应恢复有效登录态，不通过关闭鉴权绕过。代码变更仅涉及异常状态映射，可以独立回退；运行配置回退会重新造成业务服务缺少当前用户，因此不作为正常回退方案。
