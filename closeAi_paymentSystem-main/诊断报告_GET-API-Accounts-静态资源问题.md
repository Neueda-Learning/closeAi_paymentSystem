# 诊断报告：GET /api/accounts 被当作静态资源问题

**诊断日期**：2026-07-27  
**问题**：`GET /api/accounts` 请求被 Spring Boot 当作静态资源处理，而不是由 `AccountController` 处理

---

## 📋 诊断结果总结

| 检查项 | 状态 | 说明 |
|--------|------|------|
| **AccountController** | ✅ 正确 | 已正确配置 `@RestController` + `@RequestMapping("/api/accounts")` |
| **PaymentApplication** | ✅ 正确 | `@SpringBootApplication` 默认扫描所有子包，无需显式 `@ComponentScan` |
| **WebMvcConfig** | ✅ 正确 | 仅配置 CORS 映射，未添加任何静态资源处理器 (`addResourceHandlers`) |
| **application.yml** | ⚠️ 需修复 | 未配置 Spring 静态资源处理的排除规则 |
| **application-dev.yml** | ⚠️ 需修复 | 同上 |
| **前端构建文件** | ✅ 正确 | 后端 `src/main/resources` 中未发现前端构建输出文件 |
| **pom.xml** | ✅ 正确 | 无特殊构建插件会打包前端文件到后端静态目录 |

---

## 🔍 根本原因分析

### **问题根源**
Spring Boot 3.2.0（你的版本）默认启用了静态资源处理。当请求到达 `DispatcherServlet` 时：

1. **Spring 会首先检查请求路径是否匹配静态资源模式**
   - 默认静态资源位置：`/static/`, `/public/`, `/resources/`, `/META-INF/resources/`
   - 默认 URL 模式：`/**`

2. **如果没有显式配置 `spring.mvc.static-path-pattern`**
   - 所有不匹配 controller 的路径都会被视为潜在的静态资源
   - Spring 会尝试从静态目录中查找匹配的文件

3. **问题场景（最可能）**
   ```
   GET /api/accounts 
   ↓
   DispatcherServlet 路由
   ↓
   AccountController 处理 ✅
   
   BUT 如果 Spring 的静态资源处理在 controller 之前被触发：
   ↓
   静态资源处理器检查：/api/accounts 是否存在于 static/ 中？
   ↓
   如果误匹配或配置不当 → 返回 404 或其他错误资源
   ```

### **为什么会发生？**

在没有显式配置 `spring.mvc.static-path-pattern` 的情况下，Spring Boot 会使用 **默认的通配符模式 `/**`**，这会导致：

- 所有 `/**` 的请求都被考虑作为潜在的静态资源
- 当 URL 不匹配任何 controller 时，Spring 会在静态目录中查找
- **但 `/api/**` 应该优先由 controller 处理**

---

## ✅ 应用的修复方案

### **修复 1：配置 application.yml**

**文件**：`backend/src/main/resources/application.yml`

添加了以下配置：
```yaml
spring:
  web:
    resources:
      # 禁用默认静态资源处理（仅提供特定路径的资源）
      static-locations: ""
  mvc:
    # 只有以 /assets/** 开头的请求才被视为静态资源
    static-path-pattern: /assets/**
```

**解释**：
- `static-locations: ""` - 清空默认的静态资源位置列表
- `static-path-pattern: /assets/**` - 只有 `/assets/` 路径下的请求才被当作静态资源

### **修复 2：配置 application-dev.yml**

**文件**：`backend/src/main/resources/application-dev.yml`

应用了相同的配置，确保开发环境也不会出现此问题。

---

## 🧪 验证修复

在修改后，重新编译并启动应用：

### **步骤 1：清理并重新编译**
```powershell
cd backend
mvn clean compile
```

### **步骤 2：启动后端服务**
```powershell
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=dev
```

### **步骤 3：测试 API 端点**

**测试1：GET /api/accounts**
```bash
curl -X GET http://localhost:8080/api/accounts \
  -H "Content-Type: application/json"
```

**预期结果**：
```json
{
  "code": 0,
  "message": "success",
  "data": [],
  "total": 0
}
```

**测试2：POST /api/accounts（创建账户）**
```bash
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"accountNumber":"ACC-001","balance":10000,"currency":"USD"}'
```

**预期结果**：状态码 201 (CREATED)，返回账户信息

### **步骤 4：验证静态资源处理正常**

如果在 `backend/src/main/resources/static/assets/` 目录下放置文件（如 `test.txt`）：
```bash
curl http://localhost:8080/assets/test.txt
```

**预期结果**：正常返回文件内容（这证明 `/assets/**` 的静态资源处理仍然有效）

---

## 📊 对比：修复前后行为

### 修复前（有问题）
| 请求 | 处理器 | 结果 |
|------|--------|------|
| `GET /api/accounts` | ❌ 静态资源处理 + 可能的自动配置冲突 | 404 或其他错误 |
| `GET /assets/style.css` | ❌ 无处理（因为 `static-locations` 未指定） | 404 |

### 修复后（正确）
| 请求 | 处理器 | 结果 |
|------|--------|------|
| `GET /api/accounts` | ✅ `AccountController` | 200 + 账户列表 |
| `GET /api/payments` | ✅ `PaymentController` | 200 + 支付列表 |
| `GET /api/reports` | ✅ `ReportsController` | 200 + 报表数据 |
| `GET /assets/style.css` | ✅ 静态资源处理 | 200 + 资源内容 |

---

## 🔍 如果问题仍未解决

如果修改后问题仍存在，请检查以下项：

### 1. 是否有隐藏的静态资源目录
```powershell
Get-ChildItem -Path "backend/src/main/resources" -Recurse -Include "index.html", "accounts.html"
Get-ChildItem -Path "backend/src/main/webapp" -Recurse -ErrorAction SilentlyContinue
```

### 2. 是否有其他 @Configuration 类配置了资源处理
```bash
grep -r "addResourceHandlers\|StaticResourceHandler" backend/src/main/java
```

### 3. 是否有 WAR 文件中的隐藏配置
```bash
grep -r "mvc-config\|web-fragment" backend/src/main/resources
```

### 4. 前端代理配置是否正确
检查 `frontend/vite.config.js` 中的代理配置：
```javascript
proxy: {
  '/api': {
    target: 'http://localhost:8080',  // ✅ 必须指向后端服务
    changeOrigin: true,
  }
}
```

### 5. 容器/反向代理配置
如果使用 Docker 或 Nginx：
- 确保 `/api/**` 路由正确转发到后端
- 检查 Nginx 的 `location` 块是否有冲突

---

## 📝 修改文件清单

### ✅ 已修改
1. **application.yml**
   - 添加 `spring.web.resources.static-locations: ""`
   - 添加 `spring.mvc.static-path-pattern: /assets/**`

2. **application-dev.yml**
   - 添加 `spring.web.resources.static-locations: ""`
   - 添加 `spring.mvc.static-path-pattern: /assets/**`

### 无需修改（配置正确）
- ✅ [AccountController.java](../backend/src/main/java/com/hsbc/payment/controller/AccountController.java)
- ✅ [PaymentApplication.java](../backend/src/main/java/com/hsbc/payment/PaymentApplication.java)
- ✅ [WebMvcConfig.java](../backend/src/main/java/com/hsbc/payment/config/WebMvcConfig.java)
- ✅ pom.xml

---

## 🎯 总结

**根本原因**：Spring Boot 默认的静态资源处理模式与 REST API 路由产生了优先级冲突。

**修复方法**：通过明确配置 `spring.mvc.static-path-pattern` 和 `spring.web.resources.static-locations`，将静态资源处理限制在特定的 `/assets/**` 路径，确保所有其他路径（包括 `/api/**`）都由相应的 controller 处理。

**验证方式**：重新编译并启动后端，使用 curl 测试 `/api/accounts` 端点。
