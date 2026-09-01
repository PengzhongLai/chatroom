# 💬 ChatRoom

一个全栈实时聊天室应用，基于 **Spring Boot + Vue 3** 构建，支持频道群聊、私聊、文件传输与在线状态管理。

[![Java 17](https://img.shields.io/badge/Java-17-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.4-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3.5-42b883)](https://vuejs.org/)
[![Vite](https://img.shields.io/badge/Vite-8-646cff)](https://vitejs.dev/)
[![CI](https://img.shields.io/badge/CI-GitHub%20Actions-blue)](https://github.com/features/actions)

---

## ✨ 功能特性

### 频道（群聊）
- 频道创建 / 加入 / 退出 / 解散
- 成员角色管理：**CREATOR > ADMIN > MEMBER**，支持提升、降级、踢出、转让
- 禁言、邀请码加入、频道信息编辑
- 消息历史分页加载、按 HistoryLevel 过滤

### 私聊
- 私聊请求状态机：`PENDING → ACTIVE / REJECTED / DELETED`
- 接受 / 拒绝 / 删除（删除时清除消息记录）

### 消息
- 实时收发（STOMP over WebSocket）
- 消息撤回（仅本人、仅频道消息）、已读回执、输入中（typing）指示
- **@提及** 高亮与内嵌提醒
- 全局消息搜索（消息内容 + 附件文件名）

### 文件
- 图片 / 文档上传，**50MB 上限，13 种 MIME 白名单**
- UUID 重命名防冲突，静态映射 `/files/**`

### 用户与实时
- JWT 无状态认证（7 天过期，BCrypt 密码加密）
- 在线状态：ONLINE / INVISIBLE / OFFLINE，WebSocket 连接时恢复
- 全局用户搜索

### 前端
- **无 UI 库**：全部自建 macOS 风格玻璃卡片组件
- 深色模式、Emoji 选择、输入框自适应高度
- 气泡弹簧动画、未读徽章脉冲、@提及高亮

---

## 🛠️ 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17 · Spring Boot 3.4.4 · Spring Security · Spring Data JPA · STOMP WebSocket |
| 数据库 | MySQL 8.0 · Flyway 迁移 · JPA `ddl-auto: validate` |
| 安全 | JWT（jjwt 0.12.6）· BCrypt |
| 前端 | Vue 3.5 · TypeScript · Vite 8 · Pinia · Vue Router · @stomp/stompjs · SockJS |
| 测试 | JUnit 5 · Mockito · Surefire + Failsafe（H2 内存库隔离） |
| CI | GitHub Actions（H2 + MySQL 双任务验证） |

---

## 📁 项目结构

```
chatroom/
├── chat-room-backend/          # Spring Boot 后端
│   ├── src/main/java/com/chatroom/
│   │   ├── controller/         # REST + WebSocket 控制器（8个）
│   │   ├── service/            # 业务逻辑（6个）
│   │   ├── repository/         # JPA 数据访问
│   │   ├── security/           # JWT 认证过滤器
│   │   ├── websocket/          # STOMP 拦截器、连接事件
│   │   └── config/             # Security/WebSocket/CORS 等配置
│   ├── src/main/resources/db/migration/  # Flyway 迁移（mysql / h2 / common）
│
└── chat-room-frontend/         # Vue 3 前端
    ├── src/
    │   ├── api/                # Axios 接口封装
    │   ├── stores/             # Pinia 状态管理（6个）
    │   ├── composables/        # STOMP 连接管理
    │   ├── components/ui/      # 自建 macOS 风格组件
    │   └── views/              # 登录 / 注册 / 主布局
    └── ...
```

---

## 🚀 快速开始

### 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 17+ |
| Node.js | 18+（建议 20+） |
| MySQL | 8.0 |

### 1. 准备数据库

```sql
CREATE DATABASE chat_room CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

数据库结构由 Flyway 在应用启动时自动迁移（`V1__initial_schema.sql` → `V2__repair_channel_ownership.sql`）。

### 2. 启动后端

```bash
cd chat-room-backend

# 配置环境变量（也可复制 .env.example 对照填写）
export DB_URL="jdbc:mysql://localhost:3306/chat_room?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8"
export DB_USERNAME=root
export DB_PASSWORD=root
export DB_MIGRATOR_USERNAME=root
export DB_MIGRATOR_PASSWORD=root
export JWT_SECRET="<请替换为至少32字节的随机字符串>"

./mvnw spring-boot:run     # Windows: .\mvnw.cmd spring-boot:run
```

PresenceService
### 3. 启动前端

```bash
cd chat-room-frontend
npm install
npm run dev
```


### 环境变量说明

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/chat_room?...` | 数据库连接 |
| `DB_USERNAME` / `DB_PASSWORD` | 无 | 应用运行账号 |
| `DB_MIGRATOR_USERNAME` / `DB_MIGRATOR_PASSWORD` | 无 | Flyway 迁移账号 |
| `JWT_SECRET` | 无（必填） | JWT 签名密钥 |
| `JWT_EXPIRATION_MS` | `604800000`（7天） | Token 有效期 |
| `FILE_UPLOAD_DIR` | `uploads` | 上传文件目录 |
| `WEBSOCKET_ALLOWED_ORIGINS` | `http://localhost:5173` | WebSocket 允许来源 |

---

## 🧪 测试

```bash
cd chat-room-backend
./mvnw test         # 单元测试（49个）
./mvnw verify       # 单元测试 + H2/Flyway 集成测试（共65个）
```

测试默认使用**随机命名的 H2 内存库**，不连接开发 MySQL；内置 `TestEnvironmentSafetyInitializer` 防止误连开发库。

CI（`.github/workflows/backend-ci.yml`）在每次 push / PR 时并行执行：
- `h2-verification`：随机 H2 内存库快速验证
- `mysql-verification`：MySQL 8.0 Service Container 从空库执行真实迁移

---

## 📡 WebSocket 要点

- STOMP over SockJS，端点 `/ws`，应用前缀 `/app`
- CONNECT 帧校验 JWT 并注入 `Principal`；**消息处理线程无 SecurityContext**，所有 `@MessageMapping` 必须通过 `Principal` 获取 userId
- 前端断线自动重连（指数退避，最多 10 次），连接前的订阅排队重放

---

## 📄 License

本项目仅供学习交流使用，未指定开源许可证（All Rights Reserved）。
