# PLM 后端 M1 按实现沉淀补齐代码与 Apifox 验证实施文档

> 日期：2026-07-06  
> 仓库：`D:\Yuewei\git\YUEWEI\plm-server`  
> 输入文档：`docs/backend-notes/2026-07-06-PLM后端M1简单登录当前用户操作日志实现沉淀.md`  
> 外部沉淀目录：`D:\Yuewei\资料\PLM\docs\后端-沉淀`

## 1. 本次目标

基于已沉淀的 M1 实现文档，核对并补齐后端代码，使系统具备以下最小闭环：

```text
账号密码登录
随机 token 会话
当前用户上下文
未登录拦截
profile / logout
操作日志写入和查询
Product 冻结动作使用当前 token 对应用户并写操作日志
```

本阶段不实现完整 RBAC，不扩展菜单/按钮权限矩阵，不做 ERP/MES/WMS/CRM 正式集成。

## 2. 开发前已阅读文档

```text
D:\Yuewei\资料\PLM\docs\文件沉淀\开发提示词.md
D:\Yuewei\git\YUEWEI\plm-server\docs\backend-notes\2026-07-06-PLM后端M1简单登录当前用户操作日志实现沉淀.md
D:\Yuewei\资料\PLM\docs\README.md
D:\Yuewei\资料\PLM\docs\01-开发框架总纲.md
D:\Yuewei\资料\PLM\docs\02-系统架构设计.md
D:\Yuewei\资料\PLM\docs\04-AI开发规范.md
D:\Yuewei\资料\PLM\docs\05-数据模型与编码规范.md
D:\Yuewei\资料\PLM\docs\07-权限与审批流规范.md
D:\Yuewei\资料\PLM\docs\08-测试验收规范.md
D:\Yuewei\资料\PLM\docs\文件沉淀\手机壳制造业PLM系统需求规格说明书-完善版.md
D:\Yuewei\资料\PLM\docs\modules\01-产品主数据与SKU.md
```

边界确认：

```text
操作日志是系统审计能力，不作为新的业务根对象。
Product freeze 是 Product 上的关键动作留痕。
账号来自 sys_user。
M1 只允许工程部两位正式种子用户 allPermissions=true。
不假装已经具备完整 RBAC。
```

## 3. 本次实际处理方式

当前仓库中已经存在 M1 登录、当前用户、操作日志相关代码和测试。本次没有重复新建另一套实现，而是按实现沉淀文档做差距核对，并补充必要的代码注释，说明阶段性设计边界和审计字段来源。

### 3.1 本轮实际修改的代码

| 文件 | 本轮处理 |
| --- | --- |
| `src/main/java/com/yuewei/plm/common/security/SimpleTokenAuthenticationFilter.java` | 补充注释：未登录时过滤器直接返回统一 JSON，避免默认 HTML/跳转响应影响接口验收 |
| `src/main/java/com/yuewei/plm/module/auth/service/impl/InMemoryTokenSessionService.java` | 补充注释：M1 阶段使用单实例内存会话，多实例前迁移 Redis 等共享存储 |
| `src/main/java/com/yuewei/plm/module/operationlog/service/impl/OperationLogServiceImpl.java` | 补充注释：操作人和 requestId 在服务层统一取值，避免各业务接口自行拼装 |
| `src/main/java/com/yuewei/plm/service/impl/ProductServiceImpl.java` | 补充注释：冻结操作人必须来自 token 上下文，不能由前端参数伪造 |

### 3.2 已核对的既有 M1 代码

#### 登录与当前用户

| 文件 | 作用 |
| --- | --- |
| `src/main/java/com/yuewei/plm/module/auth/controller/AuthController.java` | 提供 `POST /api/v1/auth/login`、`GET /api/v1/auth/profile`、`POST /api/v1/auth/logout` |
| `src/main/java/com/yuewei/plm/module/auth/dto/LoginDTO.java` | 登录入参 |
| `src/main/java/com/yuewei/plm/module/auth/vo/LoginVO.java` | 登录返回 token、过期时间、当前用户 |
| `src/main/java/com/yuewei/plm/module/auth/vo/CurrentUserVO.java` | 当前用户返回对象 |
| `src/main/java/com/yuewei/plm/module/auth/service/AuthService.java` | 登录、profile、logout 服务接口 |
| `src/main/java/com/yuewei/plm/module/auth/service/TokenSessionService.java` | token 会话接口 |
| `src/main/java/com/yuewei/plm/module/auth/service/impl/AuthServiceImpl.java` | 从 `sys_user` 查账号，校验 BCrypt 密码，创建 token，并写登录/退出日志 |
| `src/main/java/com/yuewei/plm/module/auth/service/impl/InMemoryTokenSessionService.java` | 生成随机 token，维护 8 小时内存会话 |
| `src/main/java/com/yuewei/plm/common/security/CurrentUser.java` | 当前登录用户模型 |
| `src/main/java/com/yuewei/plm/common/security/CurrentUserContext.java` | 当前用户 ThreadLocal 上下文 |
| `src/main/java/com/yuewei/plm/common/security/SimpleTokenAuthenticationFilter.java` | 解析 Bearer token，校验会话，写入 `CurrentUserContext` 和 Spring Security 上下文 |

#### 操作日志

| 文件 | 作用 |
| --- | --- |
| `src/main/java/com/yuewei/plm/module/operationlog/controller/OperationLogController.java` | 提供 `GET /api/v1/operation-logs` 和 `POST /api/v1/operation-logs/test` |
| `src/main/java/com/yuewei/plm/module/operationlog/entity/OperationLog.java` | `plm_operation_log` 实体 |
| `src/main/java/com/yuewei/plm/module/operationlog/repository/OperationLogRepository.java` | 操作日志 Mapper |
| `src/main/java/com/yuewei/plm/module/operationlog/service/OperationLogService.java` | 写日志和分页查询接口 |
| `src/main/java/com/yuewei/plm/module/operationlog/service/OperationLogCreateCommand.java` | 写日志命令对象 |
| `src/main/java/com/yuewei/plm/module/operationlog/service/impl/OperationLogServiceImpl.java` | 统一组装操作人、requestId、请求信息、业务对象并落库 |
| `src/main/java/com/yuewei/plm/module/operationlog/constant/OperationActionConstants.java` | 动作常量，包括 `AUTH_LOGIN`、`AUTH_LOGOUT`、`TEST_WRITE`、`PRODUCT_FREEZE` |
| `src/main/java/com/yuewei/plm/module/operationlog/dto/OperationLogQueryDTO.java` | 日志分页查询条件 |
| `src/main/java/com/yuewei/plm/module/operationlog/dto/OperationLogTestDTO.java` | 测试写日志入参 |
| `src/main/java/com/yuewei/plm/module/operationlog/vo/OperationLogVO.java` | 日志查询返回对象 |

#### Product 冻结留痕

| 文件 | 作用 |
| --- | --- |
| `src/main/java/com/yuewei/plm/controller/ProductController.java` | `POST /api/v1/products/{id}/freeze` 接收产品 ID 和冻结原因 |
| `src/main/java/com/yuewei/plm/service/ProductService.java` | freeze 方法接收 `productId`、`reason`、`HttpServletRequest` |
| `src/main/java/com/yuewei/plm/service/impl/ProductServiceImpl.java` | 从 `CurrentUserContext` 获取操作人，更新冻结字段，并写 `PRODUCT_FREEZE` 操作日志 |

## 4. 数据库脚本

脚本：

```text
src/main/resources/db/migration/V20260703_1600__m1_auth_and_operation_log.sql
```

脚本内容：

```text
创建或补齐 sys_user。
创建 plm_operation_log。
为 sys_user 增加 username、password_hash、display_name、department_name、formal_flag、all_permissions、status 等字段。
为 plm_operation_log 增加 requestId、操作人、动作、业务对象、请求信息、详情 JSON、时间等字段。
插入工程部两位 M1 种子用户。
```

种子用户：

```text
username: engineer01
password: plm123456
allPermissions: true

username: engineer02
password: plm123456
allPermissions: true
```

## 5. 自动化测试结果

本轮执行命令：

```powershell
cd D:\Yuewei\git\YUEWEI\plm-server
& "D:\Yuewei\IntelliJ IDEA 2026.1.2\plugins\maven\lib\maven3\bin\mvn.cmd" test
```

本轮结果：

```text
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Finished at: 2026-07-06T10:55:41+08:00
```

覆盖的关键测试：

| 测试文件 | 验证点 |
| --- | --- |
| `src/test/java/com/yuewei/plm/module/auth/service/impl/AuthServiceImplTest.java` | 正确账号可登录，错误密码返回认证错误 |
| `src/test/java/com/yuewei/plm/module/auth/service/impl/InMemoryTokenSessionServiceTest.java` | token 创建和读取当前用户 |
| `src/test/java/com/yuewei/plm/common/security/SimpleTokenAuthenticationFilterTest.java` | 缺 token 返回 40101，有效 token 写入当前用户 |
| `src/test/java/com/yuewei/plm/module/operationlog/service/impl/OperationLogServiceImplTest.java` | 写日志包含 requestId、操作人、动作、业务对象 |
| `src/test/java/com/yuewei/plm/service/impl/ProductServiceImplTest.java` | Product 冻结使用当前用户，并写 `PRODUCT_FREEZE` 操作日志 |

说明：Maven 输出中存在 JDK/Mockito 动态 agent 相关 warning，但测试结果为 14 个通过、0 失败、0 错误。

## 6. 本地启动方式

M1 验证必须打开安全开关：

```powershell
cd D:\Yuewei\git\YUEWEI\plm-server
$env:APP_SECURITY_ENABLED="true"
& "D:\Yuewei\IntelliJ IDEA 2026.1.2\plugins\maven\lib\maven3\bin\mvn.cmd" spring-boot:run
```

如果 `APP_SECURITY_ENABLED=false`，业务接口会放行，不适合验收“未登录访问业务接口返回未登录”。

## 7. Apifox 环境配置

新建环境：

```text
环境名称：PLM 后端本地 M1
baseUrl：http://localhost:8080
username：engineer01
password：plm123456
token：空
requestId：m1-apifox-log-001
productFreezeRequestId：m1-product-freeze-001
productId：填写一个实际存在、未发布、可冻结的 Product ID
```

通用 Header：

```text
Content-Type: application/json
```

登录后接口 Header：

```text
Authorization: Bearer {{token}}
```

带链路号的接口 Header：

```text
X-Request-Id: {{requestId}}
```

## 8. Apifox 详细测试步骤

### 8.1 健康检查

请求：

```text
GET {{baseUrl}}/api/v1/health
```

预期：

```text
HTTP 200
code = 0
data.status = UP
requestId 有值
```

断言：

```javascript
const json = pm.response.json();
pm.test("健康检查成功", function () {
  pm.expect(pm.response.code).to.eql(200);
  pm.expect(json.code).to.eql(0);
  pm.expect(json.data.status).to.eql("UP");
  pm.expect(json.requestId).to.not.be.empty;
});
```

### 8.2 未登录访问业务接口失败

请求：

```text
GET {{baseUrl}}/api/v1/operation-logs
```

Header：

```text
不要携带 Authorization
```

预期：

```text
HTTP 401
code = 40101
message = 未登录或登录已失效
```

断言：

```javascript
const json = pm.response.json();
pm.test("未登录被拦截", function () {
  pm.expect(pm.response.code).to.eql(401);
  pm.expect(json.code).to.eql(40101);
  pm.expect(json.message).to.contain("未登录");
});
```

### 8.3 错误账号密码登录失败

请求：

```text
POST {{baseUrl}}/api/v1/auth/login
```

Body：

```json
{
  "username": "wrong-user",
  "password": "wrong-password"
}
```

预期：

```text
code = 40101
message = 账号或密码错误
data 不应包含 token
```

断言：

```javascript
const json = pm.response.json();
pm.test("错误账号密码不能登录", function () {
  pm.expect(json.code).to.eql(40101);
  pm.expect(json.message).to.eql("账号或密码错误");
});
```

### 8.4 正确账号密码登录成功

请求：

```text
POST {{baseUrl}}/api/v1/auth/login
```

Body：

```json
{
  "username": "{{username}}",
  "password": "{{password}}"
}
```

预期：

```text
HTTP 200
code = 0
data.token 有值
data.tokenType = Bearer
data.user.username = engineer01
data.user.allPermissions = true
```

后置脚本：

```javascript
const json = pm.response.json();
pm.test("登录成功", function () {
  pm.expect(pm.response.code).to.eql(200);
  pm.expect(json.code).to.eql(0);
  pm.expect(json.data.token).to.not.be.empty;
  pm.expect(json.data.tokenType).to.eql("Bearer");
  pm.expect(json.data.user.username).to.eql(pm.environment.get("username"));
  pm.expect(json.data.user.allPermissions).to.eql(true);
});
pm.environment.set("token", json.data.token);
```

### 8.5 获取当前用户 profile

请求：

```text
GET {{baseUrl}}/api/v1/auth/profile
```

Header：

```text
Authorization: Bearer {{token}}
```

预期：

```text
HTTP 200
code = 0
data.username = engineer01
data.displayName 有值
data.allPermissions = true
```

断言：

```javascript
const json = pm.response.json();
pm.test("profile 返回当前用户", function () {
  pm.expect(pm.response.code).to.eql(200);
  pm.expect(json.code).to.eql(0);
  pm.expect(json.data.username).to.eql(pm.environment.get("username"));
  pm.expect(json.data.displayName).to.not.be.empty;
  pm.expect(json.data.allPermissions).to.eql(true);
});
```

### 8.6 测试写操作日志

请求：

```text
POST {{baseUrl}}/api/v1/operation-logs/test
```

Header：

```text
Authorization: Bearer {{token}}
X-Request-Id: {{requestId}}
Content-Type: application/json
```

Body：

```json
{
  "businessType": "M1_TEST",
  "businessId": "apifox-001",
  "businessCode": "M1-TEST-001",
  "businessName": "M1 Apifox 测试日志",
  "detail": {
    "source": "apifox",
    "purpose": "verify operation log"
  }
}
```

预期：

```text
HTTP 200
code = 0
data.logId 有值
requestId = m1-apifox-log-001
```

后置脚本：

```javascript
const json = pm.response.json();
pm.test("测试日志写入成功", function () {
  pm.expect(pm.response.code).to.eql(200);
  pm.expect(json.code).to.eql(0);
  pm.expect(json.data.logId).to.not.be.undefined;
  pm.expect(json.requestId).to.eql(pm.environment.get("requestId"));
});
pm.environment.set("logId", json.data.logId);
```

### 8.7 按 requestId 查询操作日志

请求：

```text
GET {{baseUrl}}/api/v1/operation-logs?requestId={{requestId}}&page=1&size=20
```

Header：

```text
Authorization: Bearer {{token}}
```

预期：

```text
HTTP 200
code = 0
content 中存在 requestId = m1-apifox-log-001 的记录
action = TEST_WRITE
businessType = M1_TEST
businessId = apifox-001
operatorUserId 有值
operatorUserName 有值
createdAt 有值
```

断言：

```javascript
const json = pm.response.json();
pm.test("能按 requestId 查到操作日志", function () {
  pm.expect(pm.response.code).to.eql(200);
  pm.expect(json.code).to.eql(0);
  const target = json.data.content.find(item => item.requestId === pm.environment.get("requestId"));
  pm.expect(target).to.not.be.undefined;
  pm.expect(target.action).to.eql("TEST_WRITE");
  pm.expect(target.businessType).to.eql("M1_TEST");
  pm.expect(target.businessId).to.eql("apifox-001");
  pm.expect(target.operatorUserId).to.not.be.undefined;
  pm.expect(target.operatorUserName).to.not.be.empty;
  pm.expect(target.createdAt).to.not.be.empty;
});
```

### 8.8 数据库直查操作日志

SQL：

```sql
select
    log_id,
    request_id,
    operator_user_id,
    operator_user_name,
    action,
    business_type,
    business_id,
    business_code,
    business_name,
    created_at
from plm_operation_log
where request_id = 'm1-apifox-log-001'
order by created_at desc
limit 20;
```

预期：

```text
能查到 TEST_WRITE 记录。
operator_user_id 不为空。
operator_user_name 不为空。
created_at 有值。
```

### 8.9 退出登录

请求：

```text
POST {{baseUrl}}/api/v1/auth/logout
```

Header：

```text
Authorization: Bearer {{token}}
```

预期：

```text
HTTP 200
code = 0
```

断言：

```javascript
const json = pm.response.json();
pm.test("退出登录成功", function () {
  pm.expect(pm.response.code).to.eql(200);
  pm.expect(json.code).to.eql(0);
});
```

### 8.10 退出后 token 失效

请求：

```text
GET {{baseUrl}}/api/v1/auth/profile
```

Header：

```text
Authorization: Bearer {{token}}
```

预期：

```text
HTTP 401
code = 40101
message = 未登录或登录已失效
```

断言：

```javascript
const json = pm.response.json();
pm.test("logout 后 token 失效", function () {
  pm.expect(pm.response.code).to.eql(401);
  pm.expect(json.code).to.eql(40101);
});
```

### 8.11 Product 冻结写真实业务日志

先重新执行登录用例，获得新的 `token`。

准备一个未发布、可冻结的 Product，设置环境变量：

```text
productId：实际产品 ID
productFreezeRequestId：m1-product-freeze-001
```

请求：

```text
POST {{baseUrl}}/api/v1/products/{{productId}}/freeze?reason=M1操作人验证
```

Header：

```text
Authorization: Bearer {{token}}
X-Request-Id: {{productFreezeRequestId}}
```

预期：

```text
HTTP 200
code = 0
```

再查询日志：

```text
GET {{baseUrl}}/api/v1/operation-logs?requestId={{productFreezeRequestId}}&page=1&size=20
```

Header：

```text
Authorization: Bearer {{token}}
```

预期：

```text
action = PRODUCT_FREEZE
businessType = PRODUCT
businessId = productId
operatorUserId = 当前登录用户 ID
operatorUserName = 当前登录用户姓名
requestId = m1-product-freeze-001
createdAt 有值
```

断言：

```javascript
const json = pm.response.json();
pm.test("Product 冻结写入真实业务日志", function () {
  pm.expect(pm.response.code).to.eql(200);
  pm.expect(json.code).to.eql(0);
  const target = json.data.content.find(item => item.requestId === pm.environment.get("productFreezeRequestId"));
  pm.expect(target).to.not.be.undefined;
  pm.expect(target.action).to.eql("PRODUCT_FREEZE");
  pm.expect(target.businessType).to.eql("PRODUCT");
  pm.expect(target.businessId).to.eql(String(pm.environment.get("productId")));
  pm.expect(target.operatorUserId).to.not.be.undefined;
  pm.expect(target.operatorUserName).to.not.be.empty;
  pm.expect(target.createdAt).to.not.be.empty;
});
```

关键验收点：

```text
Product freeze 不使用前端传入的 operator。
操作人来自当前 token 对应的 CurrentUserContext。
日志中必须能查到 PRODUCT_FREEZE、PRODUCT、productId、operatorUserId、operatorUserName、requestId、createdAt。
```

## 9. 验收清单

| 推进条件 | 验证方式 | 当前状态 |
| --- | --- | --- |
| 正确账号密码可以登录 | Apifox 8.4、`AuthServiceImplTest` | 已覆盖 |
| 错误账号密码返回明确错误 | Apifox 8.3、`AuthServiceImplTest` | 已覆盖 |
| 携带 token 能获取 profile | Apifox 8.5 | 已覆盖 |
| 未携带 token 访问业务接口返回未登录 | Apifox 8.2、`SimpleTokenAuthenticationFilterTest` | 已覆盖 |
| 调用测试写日志接口后能查询到记录 | Apifox 8.6、8.7、8.8、`OperationLogServiceImplTest` | 已覆盖 |
| 日志包含业务对象、动作、操作人、requestId、时间 | Apifox 8.7、8.8、自动化测试 | 已覆盖 |
| Product freeze 写真实业务日志 | Apifox 8.11、`ProductServiceImplTest` | 已覆盖 |

## 10. 后续建议

```text
M2 开始，发布、审批、作废、文件上传、BOM 冻结等关键动作统一接入 OperationLogService。
多实例部署前，将 InMemoryTokenSessionService 替换为 RedisTokenSessionService。
完整 RBAC 等权限矩阵确认后再实现，不在 M1 范围内扩大。
```
