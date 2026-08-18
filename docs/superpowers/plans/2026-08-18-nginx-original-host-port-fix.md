# PLM Nginx Original Host Port Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the browser-visible Host port through the PLM Nginx proxy so same-origin login requests on a non-default port are not rejected by Spring CORS.

**Architecture:** Keep the fix at the Web reverse-proxy boundary by forwarding `$http_host` instead of `$host`. Protect the behavior with the repository's existing deployment contract test and document the production diagnosis and redeployment constraint without changing backend CORS policy or Compose mount behavior.

**Tech Stack:** Nginx, Java 17, JUnit 5, AssertJ, Maven, Vue 3, Vite, Docker Compose, GitHub CLI.

---

### Task 1: Add the failing Nginx proxy regression contract

**Files:**
- Modify: `plm-server/src/test/java/com/yuewei/plm/ops/DeploymentRecoveryContractTest.java`
- Test: `plm-server/src/test/java/com/yuewei/plm/ops/DeploymentRecoveryContractTest.java`

- [ ] **Step 1: Write the failing test**

Add this independent test after `baotaServerHasBoundedRecoveryResourcesAndEndpointHealthCheck`:

```java
@Test
void productionWebProxyPreservesOriginalHostAndPort() throws Exception {
    String nginx = Files.readString(REPOSITORY_ROOT.resolve("plm-web/nginx.conf"));

    assertThat(nginx).contains("proxy_set_header Host $http_host;");
    assertThat(nginx).doesNotContain("proxy_set_header Host $host;");
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
mvn -f plm-server/pom.xml \
  -Dtest=DeploymentRecoveryContractTest#productionWebProxyPreservesOriginalHostAndPort \
  test
```

Expected: FAIL because `plm-web/nginx.conf` contains `proxy_set_header Host $host;` and does not contain `$http_host`.

### Task 2: Apply the minimal Nginx fix and document operations

**Files:**
- Modify: `plm-web/nginx.conf`
- Modify: `docs/运维/2026-08-17-PLM存储恢复与部署运行手册.md`
- Test: `plm-server/src/test/java/com/yuewei/plm/ops/DeploymentRecoveryContractTest.java`

- [ ] **Step 1: Apply the minimal proxy change**

Replace only the Host forwarding line:

```nginx
proxy_set_header Host $http_host;
```

Leave `proxy_pass`, `X-Real-IP`, `X-Forwarded-For`, and `X-Forwarded-Proto` unchanged.

- [ ] **Step 2: Run the focused test and verify GREEN**

Run:

```bash
mvn -f plm-server/pom.xml \
  -Dtest=DeploymentRecoveryContractTest#productionWebProxyPreservesOriginalHostAndPort \
  test
```

Expected: PASS with one test executed and zero failures.

- [ ] **Step 3: Add the production diagnosis to the runbook**

Append this subsection under `## 4. 验证命令` after the existing expected-results paragraph:

````markdown
### 非默认端口登录 403

Web 服务通过 `8111` 等非默认端口发布时，`plm-web/nginx.conf` 必须使用
`proxy_set_header Host $http_host;`，以保留浏览器 Origin 中的端口。使用 `$host`
会丢失端口，使 Spring CORS 将同源登录请求误判为跨域请求并返回 HTTP 403。

验证当前容器配置：

```bash
docker exec plm-web nginx -T 2>&1 | grep -F 'proxy_set_header Host $http_host;'
curl --request OPTIONS --include \
  --header 'Host: plm.example.invalid:8111' \
  --header 'Origin: http://plm.example.invalid:8111' \
  --header 'Access-Control-Request-Method: POST' \
  http://127.0.0.1:8111/api/v1/auth/login
```

预期预检请求返回 HTTP 200。直接修改运行容器只会改变该容器的可写层；重新创建
旧镜像会丢失热修复。仓库修复合入后必须重新构建 Web 镜像，再执行重新创建。
````

- [ ] **Step 4: Re-run the focused contract test**

Run the focused Maven command from Step 2 again.

Expected: PASS.

- [ ] **Step 5: Commit the implementation**

```bash
git add \
  plm-web/nginx.conf \
  plm-server/src/test/java/com/yuewei/plm/ops/DeploymentRecoveryContractTest.java \
  docs/运维/2026-08-17-PLM存储恢复与部署运行手册.md \
  docs/superpowers/plans/2026-08-18-nginx-original-host-port-fix.md
git diff --cached --check
git commit -m "fix: preserve nginx host port"
```

### Task 3: Run the complete relevant verification set

**Files:**
- Verify: `plm-server/pom.xml`
- Verify: `plm-web/package.json`
- Verify: `docker-compose.baota.yml`

- [ ] **Step 1: Run the PLM Server test suite**

```bash
mvn -f plm-server/pom.xml test
```

Expected: BUILD SUCCESS with zero test failures.

- [ ] **Step 2: Run Web static checks and tests**

```bash
npm --prefix plm-web run type-check
npm --prefix plm-web run test:run
npm --prefix plm-web run build
```

Expected: all commands exit zero; Vitest reports zero failed tests and Vite produces `plm-web/dist`.

- [ ] **Step 3: Render the production Compose file with non-secret validation values**

```bash
env \
  'DB_URL=jdbc:postgresql://172.19.49.226:5432/plm_data_management?currentSchema=plm' \
  'DB_USERNAME=compose-validation-user' \
  'DB_PASSWORD=compose-validation-not-a-secret' \
  'APP_SECURITY_DEV_TOKEN=compose-validation-token' \
  docker compose -f docker-compose.baota.yml config --quiet
```

Expected: exit zero with no production credentials required or printed.

- [ ] **Step 4: Review repository state and scope**

```bash
git diff --check
git status -sb
git log --oneline origin/main..HEAD
git diff --stat origin/main..HEAD
```

Expected: no unstaged implementation changes after the commit, no secrets, and only the approved recovery branch changes relative to `origin/main`.

### Task 4: Push the branch and create a Draft PR

**Files:**
- Publish branch: `ops/plm-storage-recovery`
- Target branch: `main`

- [ ] **Step 1: Verify GitHub authentication and branch identity**

```bash
gh auth status
git branch --show-current
gh repo view Pazuma/YueweiPLM --json nameWithOwner,defaultBranchRef
```

Expected: authenticated GitHub CLI, current branch `ops/plm-storage-recovery`, default branch `main`.

- [ ] **Step 2: Push with upstream tracking**

```bash
git push -u origin ops/plm-storage-recovery
```

Expected: remote branch is created and tracks `origin/ops/plm-storage-recovery`.

- [ ] **Step 3: Create a Draft PR**

Create a Markdown body covering:

- the private PostgreSQL and Flyway recovery safeguards already committed on the branch;
- the Nginx `$host` port-loss root cause and `$http_host` fix;
- production impact: login 403 resolved without backend CORS expansion;
- Maven, Vitest, type-check, Vite build, and Compose validation results;
- the fact that production `.env` and credentials are excluded.

Create the exact PR body in a temporary file:

```bash
PR_BODY_FILE=$(mktemp -t plm-storage-recovery-pr.XXXXXX.md)
printf '%s\n' \
  '## Summary' \
  '- pins PLM datasource and Flyway to the private PostgreSQL plm schema contract' \
  '- bounds server restart and resource usage and documents the 1 TB storage migration' \
  '- preserves the original Nginx Host port so browser login is not rejected by Spring CORS' \
  '' \
  '## Root cause' \
  'Nginx used `$host`, which dropped the published `8111` port. The backend then compared a Host without the port to the browser Origin with the port and returned HTTP 403.' \
  '' \
  '## Validation' \
  '- Maven server test suite' \
  '- Web type-check, Vitest suite, and production build' \
  '- Docker Compose production configuration render with non-secret validation values' \
  '- Production health, login, project-list, Flyway, and private JDBC checks completed separately' \
  '' \
  '## Security' \
  'Production `.env` files and credentials are not included.' \
  > "$PR_BODY_FILE"
```

Run:

```bash
gh pr create \
  --repo Pazuma/YueweiPLM \
  --base main \
  --head ops/plm-storage-recovery \
  --draft \
  --title "ops: harden PLM production recovery" \
  --body-file "$PR_BODY_FILE"
```

Expected: GitHub returns the new Draft PR URL.

- [ ] **Step 4: Verify the remote PR and branch**

```bash
gh pr view --repo Pazuma/YueweiPLM --json number,title,state,isDraft,url,headRefName,baseRefName
git status -sb
```

Expected: the PR is OPEN and draft, targets `main`, uses `ops/plm-storage-recovery`, and the local branch tracks the remote with a clean worktree.
