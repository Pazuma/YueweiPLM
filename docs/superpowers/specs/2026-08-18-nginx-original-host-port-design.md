# PLM Nginx 原始 Host 与端口保留修复设计

## 背景

PLM 生产 Web 入口通过宿主机端口 `8111` 暴露，Nginx 再将 `/api/` 请求代理到 `plm-server:8080`。现有配置使用 `proxy_set_header Host $host;`，`$host` 不保留客户端请求中的端口，导致后端看到的 Host 与浏览器 Origin 不一致。Spring CORS 因此把同源登录请求判定为跨域请求并返回 HTTP 403。

生产现场已验证：将 Host 头改为保留原始主机和端口后，预检请求返回 HTTP 200，登录、健康检查和项目列表查询恢复正常。

## 目标

- Nginx 将浏览器请求的原始 Host 和端口转发给 PLM 后端。
- 为该行为增加自动化回归契约，防止后续镜像重建重新引入登录 403。
- 在运维文档中记录根因、验证方法和重新部署注意事项。
- 不扩大到后端 CORS 重构、Compose bind mount 或生产密钥管理。

## 设计

### Nginx 配置

在 `plm-web/nginx.conf` 中，将：

```nginx
proxy_set_header Host $host;
```

改为：

```nginx
proxy_set_header Host $http_host;
```

`$http_host` 保留请求头中的端口，因此浏览器访问 `http://<host>:8111` 时，后端能够识别请求为同源请求。其余代理头和路由保持不变。

### 回归测试

在现有 `DeploymentRecoveryContractTest` 中新增一个独立测试：

- 读取 `plm-web/nginx.conf`。
- 断言存在 `proxy_set_header Host $http_host;`。
- 断言不存在 `proxy_set_header Host $host;`。

测试必须先在旧配置上失败，再修改生产配置使其通过。

### 运维文档

在 PLM 存储恢复与部署运行手册中补充：

- 使用非默认端口发布 Web 服务时必须保留原始 Host 端口。
- 登录请求返回 CORS 403 时的诊断方法。
- 生产容器内热修复不会自动改变旧镜像；重新构建 Web 镜像后，仓库中的修复才会成为镜像默认配置。

## 验证

- 运行部署恢复契约测试，确认新增测试经历失败到通过。
- 运行 PLM Server 完整测试套件。
- 运行 PLM Web 生产构建。
- 使用占位环境变量执行宝塔 Compose 配置渲染，确认没有提交或打印真实生产密钥。
- 审查最终 Git diff，确保只包含已批准的 Nginx 修复、回归测试和运维说明。

## 发布

- 在现有 `ops/plm-storage-recovery` 分支提交实现。
- 推送该分支到 `origin`。
- 向 `main` 创建 Draft PR，说明根因、影响、验证命令和生产现场结果。

## 非目标

- 不提交生产 `.env`、数据库密码或其他密钥。
- 不增加 Nginx 配置 bind mount。
- 不修改业务 API、数据库结构或后端 CORS 白名单。
- 不重建或重新部署生产容器。
