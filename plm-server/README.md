# PLM Server

## 项目定位

该工程是手机壳制造业 PLM 系统的 Spring Boot 后端骨架，遵循当前文档基线：

- 核心对象仅保留 `Customer / Product / Order / ProductionOrder / Process / Inventory / Workstation`
- 接口统一前缀：`/api/v1`
- 当前已落地 `Product` 示例链路，供其他核心对象按同模式扩展

## 当前结构

```text
com.yuewei.plm
├── controller
│   ├── ProductController
│   └── dto
├── service
│   ├── ProductService
│   ├── impl
│   └── vo
├── repository
│   ├── ProductRepository
│   └── entity
├── common
│   ├── config
│   ├── constant
│   ├── exception
│   ├── interceptor
│   ├── util
│   └── vo
└── infrastructure
    ├── mq
    ├── sse
    └── storage
```

## 已完成内容

- Spring Boot 3 + MyBatis-Plus + PostgreSQL 基础配置
- `Product` 的 Controller / DTO / Service / VO / Repository / Entity
- 统一响应体 `ResponseVO`
- 全局异常处理
- 请求日志拦截器
- OpenAPI 基础配置
- Redis、SSE、MQ、Storage 的基础占位接口

## 本地配置

默认开发库连接：

- host: `localhost`
- port: `5433`
- database: `plm`
- username: `plm`
- password: `plm123`

配置文件：

- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`

## 后续扩展建议

建议按 `Product` 的模式继续扩：

1. `Customer`
2. `Order`
3. `ProductionOrder`
4. `Process`
5. `Inventory`
6. `Workstation`
7. `Attachment / Approval / System`

## 说明

当前机器未检测到 `mvn` 或 `gradle`，所以这次先完成了完整工程骨架和代码结构落地，尚未执行本地编译。
