# Security Filter Registration Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure login-issued tokens are recognized on the immediately following authenticated request.

**Architecture:** Keep `SimpleTokenAuthenticationFilter` as an injectable Spring bean and keep `SecurityConfig.addFilterBefore(...)` as the single place it is inserted into the Spring Security chain. Add a disabled `FilterRegistrationBean` so Spring Boot does not also register the same `OncePerRequestFilter` as a container-level Servlet filter.

**Tech Stack:** Spring Boot, Spring Security, Servlet FilterRegistrationBean, JUnit 5, AssertJ.

## Global Constraints

- Do not change token generation, token lifetime, or business authorization rules.
- Do not disable `app.security.enabled`.
- Preserve the existing JSON error body and business code semantics.
- Keep the fix scoped to authentication filter registration and its regression tests.

---

### Task 1: Disable Servlet Auto-Registration For The Token Filter

**Files:**
- Modify: `src/main/java/com/yuewei/plm/common/config/SecurityConfig.java`
- Create: `src/test/java/com/yuewei/plm/common/config/SecurityConfigTest.java`
- Test: `src/test/java/com/yuewei/plm/common/config/SecurityConfigTest.java`
- Test: `src/test/java/com/yuewei/plm/common/security/SimpleTokenAuthenticationFilterTest.java`

**Interfaces:**
- Consumes: `SimpleTokenAuthenticationFilter` Spring bean already injected into `SecurityConfig`.
- Produces: `FilterRegistrationBean<SimpleTokenAuthenticationFilter> simpleTokenAuthenticationFilterRegistration(SimpleTokenAuthenticationFilter filter)` with `enabled=false`.

- [ ] **Step 1: Write the failing registration test**

Create `src/test/java/com/yuewei/plm/common/config/SecurityConfigTest.java`:

```java
package com.yuewei.plm.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuewei.plm.common.security.SimpleTokenAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

class SecurityConfigTest {

    @Test
    void simpleTokenAuthenticationFilterIsNotAutoRegisteredAsServletFilter() {
        SimpleTokenAuthenticationFilter filter = mock(SimpleTokenAuthenticationFilter.class);
        SecurityConfig config = new SecurityConfig(filter, new ObjectMapper(), new AppProperties());

        FilterRegistrationBean<SimpleTokenAuthenticationFilter> registration =
            config.simpleTokenAuthenticationFilterRegistration(filter);

        assertThat(registration.getFilter()).isSameAs(filter);
        assertThat(registration.isEnabled()).isFalse();
    }
}
```

- [ ] **Step 2: Run the new test to verify it fails**

Run: `.\.codex_tmp\apache-maven\bin\mvn.cmd -Dtest=SecurityConfigTest test`

Expected: FAIL because `SecurityConfig.simpleTokenAuthenticationFilterRegistration(...)` does not exist.

- [ ] **Step 3: Add the minimal registration bean**

Modify `src/main/java/com/yuewei/plm/common/config/SecurityConfig.java`:

```java
import org.springframework.boot.web.servlet.FilterRegistrationBean;
```

Add this bean inside `SecurityConfig`:

```java
    @Bean
    public FilterRegistrationBean<SimpleTokenAuthenticationFilter> simpleTokenAuthenticationFilterRegistration(
            SimpleTokenAuthenticationFilter filter) {
        FilterRegistrationBean<SimpleTokenAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
```

- [ ] **Step 4: Run targeted unit tests**

Run: `.\.codex_tmp\apache-maven\bin\mvn.cmd -Dtest=SecurityConfigTest,SimpleTokenAuthenticationFilterTest,InMemoryTokenSessionServiceTest test`

Expected: PASS. The new registration test passes, existing filter and token session behavior remain unchanged.

- [ ] **Step 5: Run the login/profile integration smoke check against the running backend**

Run:

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/auth/login -ContentType 'application/json' -Body '{"username":"engineer01","password":"plm123456"}'
$token = $login.data.token
Invoke-RestMethod -Method Get -Uri http://localhost:8080/api/v1/auth/profile -Headers @{ Authorization = "Bearer $token" } | ConvertTo-Json -Depth 10
```

Expected: JSON response with `code` equal to `0` and `data.username` equal to `engineer01`.

- [ ] **Step 6: If the smoke check still fails, restart the backend and repeat Step 5**

Run the existing backend start command used by this workspace, then rerun Step 5.

Expected: JSON response with `code` equal to `0` and `data.username` equal to `engineer01`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/yuewei/plm/common/config/SecurityConfig.java src/test/java/com/yuewei/plm/common/config/SecurityConfigTest.java docs/superpowers/plans/2026-07-21-security-filter-registration-fix.md
git commit -m "fix: register token filter only in security chain"
```
